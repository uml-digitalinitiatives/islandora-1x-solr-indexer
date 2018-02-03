package ca.umanitoba.dam.islandora.fc3indexer;

import static org.apache.camel.component.solr.SolrConstants.OPERATION_DELETE_BY_ID;
import static org.apache.camel.component.solr.SolrConstants.OPERATION_INSERT;
import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;
import static org.apache.camel.Exchange.HTTP_URI;
import static org.apache.camel.LoggingLevel.DEBUG;
import static org.apache.camel.LoggingLevel.ERROR;
import static org.apache.camel.LoggingLevel.INFO;
import static org.apache.camel.LoggingLevel.TRACE;
import static org.apache.camel.LoggingLevel.WARN;
import static org.apache.camel.builder.PredicateBuilder.in;
import static org.apache.camel.builder.PredicateBuilder.not;
import static org.slf4j.LoggerFactory.getLogger;

import org.apache.camel.BeanInject;
import org.apache.camel.Processor;
import org.apache.camel.PropertyInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.Namespaces;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.solr.common.SolrException;
import org.slf4j.Logger;
import org.w3c.dom.Document;

/**
 * Fedora 3 to Solr indexing routes.
 *
 * @author whikloj
 */
public class FedoraSolrIndexer extends RouteBuilder implements RoutesBuilder {

    private static final Logger LOGGER = getLogger(FedoraSolrIndexer.class);

    private AggregationStrategy stringConcatStrategy = new StringConcatAggregator();

    private AggregationStrategy latestStrategy = new UseLatestAggregationStrategy();

    @PropertyInject(value = "completion.timeout")
    public long completionTimeout;

    @PropertyInject(value = "reindexer.port")
    private int restPortNum;
    
    @PropertyInject(value = "reindexer.path")
    private String restPath;

    private Processor string2xml = new StringToXmlProcessor();

    private final String foxmlNS = "info:fedora/fedora-system:def/foxml#";

    private final Namespaces ns = new Namespaces("default", foxmlNS).add("foxml", foxmlNS);

    @Override
    public void configure() throws Exception {

    		final String fullPath = (!restPath.startsWith("/") ? "/" : "" + restPath);
        //restConfiguration().component("jetty").host("localhost").contextPath(restPath).port(restPortNum);

        /**
         * A REST endpoint on localhost to force a re-index.
         * Called from: REST request to http://localhost:<reindexer.port>/reindex/<PID>
         * Calls:       JMS queue - internal
         */
        //rest("/reindex")
        // .id("Fc3SolrRestEndpoint")
        // .description("Rest endpoint to reindex a specific PID")
        // .get("/{pid}")
        // .to("direct:restToReindex");

        from("direct:restToReindex")
            .routeId("rest-to-reindex")
            .description("Parse REST request and re-index directly, skipping JMS queue")
            .setProperty("pid", header("pid"))
            .removeHeaders("*")
            .setHeader("pid", exchangeProperty("pid"))
            .setHeader("methodName", constant("indexObject"))
            .to("{{queue.internal}}")
            .log(INFO, LOGGER, "Added ${property[pid]} to direct reindex");

        /**
         * Sits on the Fedora event queue and aggregates incoming messages to avoid over processing.
         * Called from: JMS queue - external
         * Calls:       JMS queue - internal
         */
        from("{{queue.incoming}}")
            .routeId("fedora-aggregator")
            .description("Fedora Message aggregator (10 sec inactivity timeout)")
            .log(DEBUG, LOGGER, "Raw Fedora Object: ${header[pid]}, method: ${header[methodName]}")
            .filter(header("pid").isNotNull())
            .aggregate(header("pid"), latestStrategy).completionTimeout(completionTimeout)
                .log(INFO, LOGGER, "Aggregated Fedora Object: ${header[pid]}, method: ${header[methodName]}")
            .to("{{queue.internal}}");

        /**
         * Grabs from the internal aggregated queue and starts processing.
         * Called from: JMS queue - internal
         * Calls:   fedora-get-object-xml
         *          fedora-delete-multicaster
         */
        from("{{queue.internal}}")
            .routeId("fedora-routing")
            .description("Fedora message routing")
            .setProperty("pid",header("pid"))
            .choice()
                .when(header("methodName").isEqualTo("purgeObject"))
                    .to("direct:fedora.delete")
                .otherwise()
                    .to("direct:fedora.getObjectXml")
            .end();

        /**
         * Call out to Fedora to get the objectXML and return it.
         * Called from: fedora-routing
         * Calls:       fedora-insert-multicaster
         */
        from("direct:fedora.getObjectXml")
            .routeId("fedora-get-object-xml")
            .description("Attempt to retrieve the object XML for the resource")
            .setHeader(HTTP_METHOD, constant("GET"))
            .setHeader(HTTP_URI, simple("{{fcrepo.baseUrl}}{{fcrepo.basePath}}/objects/$simple{property[pid]}/objectXML"))
            .log(DEBUG, LOGGER, "Getting foxml ${property[pid]}")
            .to("http4://localhost?authUsername={{fcrepo.authUser}}&authPassword={{fcrepo.authPassword}}")
            .choice()
                .when(header(HTTP_RESPONSE_CODE).isEqualTo(200))
                    .to("direct:fedora.insert")
                .otherwise()
                    .log(ERROR, LOGGER, "Unable to get {{fcrepo.baseUrl}}{{fcrepo.basePath}}/objects/$simple{property[pid]}/objectXML")
            .end();

        /**
         * Determine if the object has Active state and re-index otherwise delete from Solr.
         *
         * Called from fedora-get-object-xml
         * Calls:   fedora-foxml-properties
         *          fedora-ds-process
         *          fedora-delete-multicaster
         *          solr-insertion
         */
        from("direct:fedora.insert")
            .routeId("fedora-insert-multicaster")
            .description("Fedora Message insert multicaster")
            .log(TRACE, LOGGER, "Started fedora-insert-multicaster")
            .log(DEBUG, LOGGER, "aggregating ${property[pid]}")
            .choice()
                .when(ns.xpath("/foxml:digitalObject/foxml:objectProperties/foxml:property[@NAME = 'info:fedora/fedora-system:def/model#state' and @VALUE = 'Active']"))
                    .multicast(stringConcatStrategy, false)
                        .to("direct:fedora.properties")
                        .split(body().tokenizeXML("datastream", "digitalObject"), stringConcatStrategy)
                            .streaming()
                            //.setHeader("foxml", constant(foxmlNS))
                            .to("direct:fedora.dsProcess")
                        .end()
                    .end()
                    .setBody(simple("<update><add><doc>${body}</doc></add></update>"))
            .to("seda:solr.update")
                .endChoice()
                .otherwise()
                    .to("direct:solr.delete")
            .end()
            .log(TRACE,  LOGGER, "Completed fedora-insert-multicaster");

        /**
         * Processes the main FOXML properties, the FOXML.xslt is the only required XML file.
         * Called from: fedora-insert-multicaster
         */
        from("direct:fedora.properties")
            .routeId("fedora-foxml-properties")
            .description("Process the FOXML digitalObject XML to get some base Solr fields (including PID)")
            .log(TRACE, LOGGER, "Started fedora-foxml-properties")
            .to("log:ca.umanitoba.dam.islandora.fc3indexer?level=TRACE")
            .setHeader("dsid", constant("FOXML"))
            .setHeader("pid", header("pid"))
            .setHeader("FEDORAUSER", exchangeProperty("fcrepo.authUser"))
            .setHeader("FEDORAPASS", exchangeProperty("fcrepo.authPassword"))
            .setHeader("FEDORAURL", exchangeProperty("fcrepo.baseUrl"))
            .setHeader("FEDORAPATH", exchangeProperty("fcrepo.basePath"))
            .to("xslt:{{xslt.path}}/FOXML.xslt?transformerFactory=#xsltTransformer")
            .to("log:ca.umanitoba.dam.islandora.fc3indexer?level=TRACE")
            .log(TRACE, LOGGER, "Completed fedora-foxml-properties");

        /**
         * Processes a single foxml:datastream element, if there is an appropriate xslt file.
         * Called from: fedora-insert-multicaster
         * Calls:       xslt-exists
         */
        from("direct:fedora.dsProcess")
            .routeId("fedora-ds-process")
            .description("Process the individual datastreams and return the Solr fields for their data.")
            .log(TRACE, LOGGER, "Started fedora-ds-process")
            .setHeader("DSID", ns.xpath("/foxml:datastream/@ID"))
            .setHeader("mimetype", ns.xpath("/foxml:datastream/foxml:datastreamVersion[last()]/@MIMETYPE"))
            .log(DEBUG, LOGGER, "DSID (${header[DSID]}), mimetype (${header[mimetype]})")
            .choice()
                .when(method(XSLTChecker.class, "exists"))
                    .to("direct:process-xslt")
                .otherwise()
                    .setBody(constant(""))
            .end()
            .to("log:ca.umanitoba.dam.islandora.fc3indexer?level=TRACE")
            .log(TRACE, LOGGER, "Completed fedora-ds-process");

        /**
         * The <DSID>.xslt exists so lets run the transform.
         * Called from: fedora-ds-process
         * Calls:   fedora-ds-isXML
         *          fedora-ds-isText
         */
        from("direct:process-xslt")
            .routeId("xslt-exists")
            .routeDescription("Processes the datastream content with the appropriate XSLT")
            .onException(javax.xml.transform.TransformerException.class)
                .handled(true)
                .setBody(constant(""))
                .log(ERROR, LOGGER, "Transform Exception in route xslt-exists for ${header[pid]}: ${exception.type} - ${exception}")
            .end()
            .onException(Exception.class)
                .handled(true)
                .setBody(constant(""))
                .log(ERROR, LOGGER, "Generic Exception (${exception.type}) for ${header[pid]} : ${exception}")
            .end()
            .choice()
                .when(in(
                    header("mimetype").isEqualTo("text/xml"),
                    header("mimetype").isEqualTo("application/xml"),
                    header("mimetype").isEqualTo("application/rdf+xml"),
                    header("mimetype").isEqualTo("text/html")))
                    .to("direct:dsXML")
                    .log(DEBUG, LOGGER, "Trying {{xslt.path}}/$simple{header[DSID]}.xslt")
                    .recipientList(simple("xslt:{{xslt.path}}/$simple{header[DSID]}.xslt?transformerFactory=#xsltTransformer"))
                    .endChoice()
                .when(header("mimetype").isEqualTo("text/plain"))
                    .to("direct:dsText")
                    .log(DEBUG, LOGGER, "Trying {{xslt.path}}/$simple{header[DSID]}.xslt")
                    .recipientList(simple("xslt:{{xslt.path}}/$simple{header[DSID]}.xslt?transformerFactory=#xsltTransformer"))
                    .endChoice()
                .otherwise()
                    .setBody(constant(""))
            .end()
            .to("log:ca.umanitoba.dam.islandora.fc3indexer?level=TRACE");

        /**
         * Push the string (in SolrInputDocument format) to Solr
         * Called from: fedora-insert-multicaster
         */
        from("seda:solr.update?blockWhenFull=true&concurrentConsumers={{concurrent.processes}}")
            .routeId("solr-insertion")
            .description("Solr Insertion")
            .onException(SolrException.class)
                .handled(true)
                .log(ERROR, LOGGER, "Solr error ${exception.type} on ${header[pid]}: ${exception.message}")
            .end()
            .onException(Exception.class)
                .handled(true)
                .log(ERROR, LOGGER, "Generic Exception (${exception.type}) for ${header[pid]} : ${exception}")
            .end()
            .removeHeaders("*")
            .setBody(body().convertToString())
            .log(TRACE, LOGGER, "Started solr-insertion")
            .to("log:ca.umanitoba.dam.islandora.fc3indexer?level=TRACE")
            .setHeader("SolrOperation", constant(OPERATION_INSERT))
            .to("{{solr.baseUrl}}")
            .log(INFO, LOGGER, "Added/Updated ${header[pid]} to Solr")
            .log(TRACE, LOGGER, "Completed solr-insertion");

        /**
         * Getting an XML datastream content from Fedora.
         * Called from: xslt-exists
         * Calls:       fedora-get-url
         */
        from("direct:dsXML")
            .routeId("fedora-ds-isXML")
            .description("The datastream has an XML/HTML mimetype")
            .removeHeaders("CamelHttp*")
            .setHeader(HTTP_METHOD, constant("GET"))
            .setHeader(HTTP_URI, simple(
                "{{fcrepo.baseUrl}}{{fcrepo.basePath}}/objects/$simple{header[pid]}/datastreams/$simple{header[DSID]}/content"))
            .log(DEBUG, LOGGER, "Getting XML datastream ${header[DSID]} for ${header[pid]}")
            .to("direct:get-url")
            .filter(body().isNotNull()).setBody(body().convertTo(Document.class));

        /**
         * Getting a text datastream content from Fedora.
         * Called from: xslt-exists
         * Calls:       fedora-get-url
         */
        from("direct:dsText")
            .routeId("fedora-ds-isText")
            .description("The datastream has a text mimetype")
            .removeHeaders("CamelHttp*")
            .setHeader(HTTP_METHOD, constant("GET"))
            .setHeader(HTTP_URI, simple(
                "{{fcrepo.baseUrl}}{{fcrepo.basePath}}/objects/$simple{header[pid]}/datastreams/$simple{header[DSID]}/content"))
            .log(DEBUG, LOGGER, "Getting Text datastream ${header[DSID]} for ${header[pid]}")
            .to("direct:get-url")
            .filter(body().isNotNull()).process(string2xml);

        /**
         * Actually gets the datastream content from Fedora.
         * Called from: fedora-ds-isXML
         *              fedora-ds-isText
         */
        from("direct:get-url")
            .routeId("fedora-get-url")
            .description("Get the HTTP_URI and check we got it, then return the message")
            .to("http4://localhost?authUsername={{fcrepo.authUser}}&authPassword={{fcrepo.authPassword}}")
            .choice()
                .when(not(header(HTTP_RESPONSE_CODE).isEqualTo(200)))
                    .log(WARN, LOGGER, "Problem getting ${header[CamelHttpUri]} received ${header[CamelHttpResponseCode]}")
                    .setBody(constant(""))
            .end();

        /**
         * Unused delete multicaster, just an end-to-end route.
         * Called from: fedora-routing
         *              fedora-insert-multicaster
         * Calls:       solr-deletion
         */
        from("direct:fedora.delete")
            .routeId("fedora-delete-multicaster")
            .description("Fedora Message delete multicaster")
            .to("seda:solr.delete");

        /**
         * Deletes from Solr by ID.
         * Called from: fedora-delete-multicaster
         */
        from("seda:solr.delete?blockWhenFull=true&concurrentConsumers={{concurrent.processes}}")
            .routeId("solr-deletion")
            .description("Solr Deletion")
            .onException(SolrException.class)
                .handled(true)
                .log(ERROR, LOGGER, "Solr error ${exception.type} on ${header[pid]}: ${exception.message}")
            .end()
            .onException(Exception.class)
                .handled(true)
                .log(ERROR, LOGGER, "Generic Exception (${exception.type}) for ${header[pid]} : ${exception}")
            .end()
            .setHeader("SolrOperation", constant(OPERATION_DELETE_BY_ID))
            .setBody(header("pid"))
            .to("{{solr.baseUrl}}")
            .log(INFO, LOGGER, "Removed ${header.pid} from Solr");

        /**
         * Dead letter logging.
         * Called from: no where yet
         */
        from("seda:dead-letter-log")
            .routeId("log-and-dead-letter")
            .description("Logs why a request is ending up here and then park it in the dead letter queue")
            .choice()
                .when(not(header("dsid").isNull()))
                    .log(WARN, LOGGER,
                    "DEAD LETTER: pid (${header[pid]}), dsid (${header[dsid]}), message - ${exception.message}")
                .otherwise()
                    .log(WARN, LOGGER, "DEAD LETTER: pid (${header[pid]}), message - ${exception.message}")
            .end()
            .to("{{queue.dead-letter}}");
    }

}
