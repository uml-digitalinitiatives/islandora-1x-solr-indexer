package ca.umanitoba.dam.islandora.fc3indexer.routes;

import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;
import static org.apache.camel.Exchange.HTTP_URI;
import static org.apache.camel.ExchangePattern.InOnly;
import static org.apache.camel.LoggingLevel.DEBUG;
import static org.apache.camel.LoggingLevel.ERROR;
import static org.apache.camel.LoggingLevel.INFO;
import static org.apache.camel.LoggingLevel.TRACE;
import static org.apache.camel.LoggingLevel.WARN;
import static org.apache.camel.builder.PredicateBuilder.in;
import static org.apache.camel.builder.PredicateBuilder.not;
import static org.apache.camel.component.solr.SolrConstants.OPERATION;
import static org.apache.camel.component.solr.SolrConstants.OPERATION_DELETE_BY_ID;
import static org.apache.camel.component.solr.SolrConstants.OPERATION_INSERT;
import static org.apache.camel.component.solr.SolrConstants.OPERATION_SOFT_COMMIT;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Processor;
import org.apache.camel.PropertyInject;
import org.apache.camel.TypeConversionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.language.xpath.XPathBuilder;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.support.builder.Namespaces;
import org.apache.solr.common.SolrException;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import ca.umanitoba.dam.islandora.fc3indexer.utils.StringConcatAggregator;
import ca.umanitoba.dam.islandora.fc3indexer.utils.StringToXmlProcessor;
import ca.umanitoba.dam.islandora.fc3indexer.utils.XSLTChecker;

/**
 * Fedora 3 to Solr indexing routes.
 *
 * @author whikloj
 */

@Component
public class FedoraSolrIndexer extends RouteBuilder {

    private static final Logger LOGGER = getLogger(FedoraSolrIndexer.class);

    private final AggregationStrategy stringConcatStrategy = new StringConcatAggregator();

    private final AggregationStrategy latestStrategy = new UseLatestAggregationStrategy();

    @PropertyInject(value = "completion.timeout")
    public long completionTimeout;

    @PropertyInject(value = "reindexer.port")
    private int restPortNum;

    @PropertyInject(value = "reindexer.path")
    private String restPath;

    @PropertyInject("custom.character.file")
    private String customCharacters;

    @PropertyInject("xslt.path")
    private String xsltPath;

    private final String foxmlNS = "info:fedora/fedora-system:def/foxml#";

    private final Namespaces ns = new Namespaces("default", foxmlNS).add("foxml", foxmlNS);

    private final XPathBuilder activeStateXpath = XPathBuilder
            .xpath("/foxml:digitalObject/foxml:objectProperties/foxml:property[@NAME = 'info:fedora/fedora-system:def/model#state' and @VALUE = 'Active']")
            .namespaces(ns).booleanResult();

    private final XPathBuilder datastreamIdXpath = XPathBuilder.xpath("/foxml:datastream/@ID", String.class)
            .namespaces(ns);

    private final XPathBuilder datastreamMimeTypeXpath = XPathBuilder
            .xpath("/foxml:datastream/foxml:datastreamVersion[last()]/@MIMETYPE", String.class)
            .namespaces(ns);

    @Override
    public void configure() throws Exception {
        getContext().setStreamCaching(true);

        final Processor string2xml = new StringToXmlProcessor(customCharacters);

        final String fullPath = (!restPath.startsWith("/") ? "/" : "") + restPath + "/";
        restConfiguration().component("jetty").host("localhost").port(restPortNum).contextPath(fullPath);

        LOGGER.debug("Property injected xslt.path is {}", xsltPath);
        /*
         * A REST endpoint on localhost to force a re-index. Called from: REST request
         * to http://localhost:<reindexer.port>/reindex/<PID> Calls: JMS queue -
         * internal
         */
        rest("reindex")
                .id("Fc3SolrRestEndpoint")
                .description("Rest endpoint to reindex a specific PID")
                .get("/{pid}")
                .to("direct:restToReindex");

        from("direct:restToReindex")
                .routeId("rest-to-reindex")
                .description("Parse REST request and re-index directly, skipping JMS queue")
                .setExchangePattern(InOnly)
                .setProperty("pid", header("pid"))
                .removeHeaders("*")
                .setHeader("pid", exchangeProperty("pid"))
                .setHeader("methodName", constant("indexObject"))
                .to("{{queue.internal}}")
                .log(INFO, LOGGER, "Added ${exchangeProperty[pid]} to direct reindex")
                .setBody(constant(""))
                .removeHeaders("*");

        /*
         * Sits on the Fedora event queue and aggregates incoming messages to avoid over
         * processing. Called from: JMS queue - external Calls: JMS queue - internal
         */
        from("{{queue.incoming}}")
                .routeId("fedora-aggregator")
                .description("Fedora Message aggregator (10 sec inactivity timeout)")
                .log(DEBUG, LOGGER, "Raw Fedora Object: ${header[pid]}, method: ${header[methodName]}")
                .filter(header("pid").isNotNull())
                .aggregate(header("pid"), latestStrategy).completionTimeout(completionTimeout)
                .log(INFO, LOGGER, "Aggregated Fedora Object: ${header[pid]}, method: ${header[methodName]}")
                .to("{{queue.internal}}");

        /*
         * Grabs from the internal aggregated queue and starts processing. Called from:
         * JMS queue - internal Calls: fedora-get-object-xml fedora-delete-multicaster
         */
        from("{{queue.internal}}")
                .routeId("fedora-routing")
                .description("Fedora message routing")
                .setProperty("pid", header("pid"))
                .choice()
                    .when(header("methodName").isEqualTo("purgeObject"))
                         .to("direct:fedora.delete")
                .otherwise()
                    .to("direct:fedora.getObjectXml")
                .end();

        /*
         * Call out to Fedora to get the objectXML and return it. Called from:
         * fedora-routing Calls: fedora-insert-multicaster
         */
        from("direct:fedora.getObjectXml")
                .routeId("fedora-get-object-xml")
                .description("Attempt to retrieve the object XML for the resource")
                .setHeader(HTTP_METHOD, constant("GET"))
                .setHeader(HTTP_URI,
                        simple("{{fcrepo.baseUrl}}{{fcrepo.basePath}}/objects/$simple{exchangeProperty[pid" +
                                "]}/objectXML"))
                .log(DEBUG, LOGGER, "Getting foxml ${exchangeProperty[pid]}")
                .to("http://localhost?authUsername={{fcrepo.authUser}}&authPassword={{fcrepo.authPassword}}&throwExceptionOnFailure=false&authenticationPreemptive=true")
                .choice()
                    .when(header(HTTP_RESPONSE_CODE).isEqualTo(200))
                        .to("direct:fedora.insert")
                .otherwise()
                     .log(ERROR, LOGGER,
                        "Unable to get {{fcrepo.baseUrl}}{{fcrepo" +
                                ".basePath}}/objects/$simple{exchangeProperty[pid]}/objectXML")
                .end();

        /*
         * Determine if the object has Active state and re-index otherwise delete from
         * Solr.
         *
         * Called from fedora-get-object-xml Calls: fedora-foxml-properties
         * fedora-ds-process fedora-delete-multicaster solr-insertion
         */
        from("direct:fedora.insert")
                .routeId("fedora-insert-multicaster")
                .description("Fedora Message insert multicaster")
                .log(TRACE, LOGGER, "Started fedora-insert-multicaster")
                .log(DEBUG, LOGGER, "aggregating ${exchangeProperty[pid]}")
                .choice()
                    .when(activeStateXpath)
                        .multicast(stringConcatStrategy, false)
                          .to("direct:fedora.properties")
                          .split(body().tokenizeXML("datastream", "digitalObject"), stringConcatStrategy)
                            .streaming()
                            .parallelProcessing()
                            .to("direct:fedora.dsProcess")
                          .end()
                        .end()
                        .setBody(simple("<update><add><doc>${body}</doc></add></update>"))
                        .to("seda:solr.update")
                    .endChoice()
                .otherwise()
                    .to("direct:fedora.delete")
                .end()
                .log(TRACE, LOGGER, "Completed fedora-insert-multicaster");

        /*
         * Processes the main FOXML properties, the FOXML.xslt is the only required XML
         * file. Called from: fedora-insert-multicaster
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
                .setHeader("DSID", constant("FOXML"))
                .log(DEBUG, LOGGER, "Processing {{xslt.path}}/FOXML.xslt")
                .choice()
                    .when(method(XSLTChecker.class, "exists"))
                    .to("xslt:{{xslt.path}}/FOXML.xslt?contentCache=false")
                .otherwise()
                    .log(ERROR, LOGGER, "Could not find {{xslt.path}}/FOXML.xslt")
                    .setBody(constant(""))

                .to("log:ca.umanitoba.dam.islandora.fc3indexer?level=TRACE")
                .log(TRACE, LOGGER, "Completed fedora-foxml-properties");

        /*
         * Processes a single foxml:datastream element, if there is an appropriate xslt
         * file. Called from: fedora-insert-multicaster Calls: xslt-exists
         */
        from("direct:fedora.dsProcess")
                .routeId("fedora-ds-process")
                .description("Process the individual datastreams and return the Solr fields for their data.")
                .log(TRACE, LOGGER, "Started fedora-ds-process")
                .setHeader("DSID", datastreamIdXpath)
                .setHeader("mimetype", datastreamMimeTypeXpath)
                .log(DEBUG, LOGGER, "DSID (${header[DSID]}), mimetype (${header[mimetype]})")
                .choice()
                    .when(method(XSLTChecker.class, "exists"))
                        .to("direct:process-xslt")
                    .otherwise()
                        .setBody(constant(""))
                .end()
                .to("log:ca.umanitoba.dam.islandora.fc3indexer?level=TRACE")
                .log(TRACE, LOGGER, "Completed fedora-ds-process");

        /*
         * The <DSID>.xslt exists so lets run the transform. Called from:
         * fedora-ds-process Calls: fedora-ds-isXML fedora-ds-isText
         */
        from("direct:process-xslt")
                .routeId("xslt-exists")
                .routeDescription("Processes the datastream content with the appropriate XSLT")
                .onException(javax.xml.transform.TransformerException.class)
                .handled(true)
                .setBody(constant(""))
                .log(ERROR, LOGGER,
                        "Transform Exception in route xslt-exists for ${header[pid]}: ${exception.type} - ${exception}")
                .end()
                .onException(Exception.class)
                .handled(true)
                .setBody(constant(""))
                .log(ERROR, LOGGER, "Generic Exception (${exception.type}) for ${header[pid]} : ${exception}")
                .end()
                .choice()
                .when(in(
                        header("mimetype").startsWith("text/xml"),
                        header("mimetype").startsWith("application/xml"),
                        header("mimetype").startsWith("application/rdf+xml"),
                        header("mimetype").startsWith("text/html")))
                        .to("direct:dsXML")
                        .log(DEBUG, LOGGER, "Trying {{xslt.path}}/$simple{header[DSID]}.xslt")
                        .recipientList(
                                 simple("xslt:{{xslt.path}}/$simple{header[DSID]}" +
                                         ".xslt?entityResolver=#cachingEntityResolver"))
                        .endChoice()
                .when(header("mimetype").startsWith("text/plain"))
                        .to("direct:dsText")
                        .log(DEBUG, LOGGER, "Trying {{xslt.path}}/$simple{header[DSID]}.xslt")
                        .recipientList(
                                 simple("xslt:{{xslt.path}}/$simple{header[DSID]}.xslt"))
                        .endChoice()
                .otherwise()
                         .setBody(constant(""))
                .end()
                .to("log:ca.umanitoba.dam.islandora.fc3indexer?level=TRACE");

        /*
         * Push the string (in SolrInputDocument format) to Solr Called from:
         * fedora-insert-multicaster
         */
        from("seda:solr.update?blockWhenFull=true&concurrentConsumers={{solr.processes}}")
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
                .setHeader(OPERATION, constant(OPERATION_INSERT))
                .to("{{solr.baseUrl}}")
                .setHeader(OPERATION, constant(OPERATION_SOFT_COMMIT))
                .to("{{solr.baseUrl}}")
                .log(INFO, LOGGER, "Added/Updated ${header[pid]} to Solr")
                .log(TRACE, LOGGER, "Completed solr-insertion");

        /*
         * Getting an XML datastream content from Fedora. Called from: xslt-exists
         * Calls: fedora-get-url
         */
        from("direct:dsXML")
                .routeId("fedora-ds-isXML")
                .description("The datastream has an XML/HTML mimetype")
                .onException(TypeConversionException.class)
                .log(WARN, "TypeConversionException: DSID ${header[DSID]} for ${header[pid]}")
                .setBody(constant(""))
                .handled(true)
                .end()
                .removeHeaders("CamelHttp*")
                .setHeader(HTTP_METHOD, constant("GET"))
                .setHeader(HTTP_URI, simple(
                        "{{fcrepo.baseUrl}}{{fcrepo.basePath}}/objects/$simple{header[pid]}/datastreams/$simple{header[DSID]}/content"))
                .log(DEBUG, LOGGER, "Getting XML datastream ${header[DSID]} for ${header[pid]}")
                .to("direct:get-url")
                .filter(body().isNotNull())
                .setBody(body());

        /*
         * Getting a text datastream content from Fedora. Called from: xslt-exists
         * Calls: fedora-get-url
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

        /*
         * Actually gets the datastream content from Fedora. Called from:
         * fedora-ds-isXML fedora-ds-isText
         */
        from("direct:get-url")
                .routeId("fedora-get-url")
                .description("Get the HTTP_URI and check we got it, then return the message")
                .onException(IOException.class)
                .maximumRedeliveries(2)
                .retriesExhaustedLogLevel(WARN)
                .handled(true)
                .to("{{queue.dead-letter}}")
                .end()
                .to("http://localhost?authUsername={{fcrepo.authUser}}&authPassword={{fcrepo.authPassword}}&setSocketTimeout=10000&authenticationPreemptive=true&throwExceptionOnFailure=false")
                .choice()
                .when(not(header(HTTP_RESPONSE_CODE).isEqualTo(200)))
                .log(WARN, LOGGER, "Problem getting ${header[CamelHttpUri]} received ${header[CamelHttpResponseCode]}")
                .setBody(constant(""))
                .end();

        /*
         * Unused delete multicaster, just an end-to-end route. Called from:
         * fedora-routing fedora-insert-multicaster Calls: solr-deletion
         */
        from("direct:fedora.delete")
                .routeId("fedora-delete-multicaster")
                .description("Fedora Message delete multicaster")
                .to("seda:solr.delete");

        /*
         * Deletes from Solr by ID. Called from: fedora-delete-multicaster
         */
        from("seda:solr.delete?blockWhenFull=true&concurrentConsumers={{solr.processes}}")
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
                .setHeader(OPERATION, constant(OPERATION_DELETE_BY_ID))
                .setBody(header("pid"))
                .to("{{solr.baseUrl}}")
                .log(INFO, LOGGER, "Removed ${header.pid} from Solr");

        /*
         * Dead letter logging. Called from: no where yet
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
