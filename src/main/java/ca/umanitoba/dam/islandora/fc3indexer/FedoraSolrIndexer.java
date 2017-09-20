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
//import org.apache.camel.Exchange;
//import org.apache.camel.Processor;
import org.apache.camel.PropertyInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.solr.common.SolrException;
import org.slf4j.Logger;
import org.w3c.dom.Document;


public class FedoraSolrIndexer extends RouteBuilder {

    private static final Logger LOGGER = getLogger(FedoraSolrIndexer.class);

    @BeanInject(value = "stringConcatAggregation")
    public AggregationStrategy stringConcatStrategy;

    @BeanInject(value = "latestAggregation")
    public AggregationStrategy latestStrategy;

    @PropertyInject(value = "completion.timeout")
    public long completionTimeout;

    @PropertyInject(value = "reindexer.port")
    private int restPortNum;

    @Override
    public void configure() throws Exception {

        restConfiguration().component("spark-rest").host("127.0.0.1").port(restPortNum);

        rest("/reindex")
            .id("Fc3SolrRestEndpoint")
            .description("Rest endpoint to reindex a specific PID")
            .get("/{pid}")
            .to("direct:to_reindex?exchangePattern=InOnly");

        from("direct:to_reindex")
            .routeId("rest-to-reindex")
            .description("Parse REST request and re-index directly, skipping JMS queue")
            .setProperty("pid", header("pid"))
            .removeHeaders("*")
            .setHeader("pid", exchangeProperty("pid"))
            .setHeader("methodName", constant("indexObject"))
            .to("{{queue.internal}}")
            .log(INFO, LOGGER, "Added ${property[pid]} to direct reindex");

        from("{{queue.incoming}}")
            .routeId("fedora-aggregator")
            .description("Fedora Message aggregator (10 sec inactivity timeout)")
            .log(DEBUG, LOGGER, "Raw Fedora Object: ${header[pid]}, method: ${header[methodName]}")
            .filter(header("pid").isNotNull())
            .aggregate(header("pid"), latestStrategy).completionTimeout(completionTimeout)
                .log(INFO, LOGGER, "Aggregated Fedora Object: ${header[pid]}, method: ${header[methodName]}")
            .to("{{queue.internal}}");

        from("{{queue.internal}}")
            .routeId("fedora-routing")
            .description("Fedora message routing")
            .setProperty("pid",header("pid"))
            .choice()
                .when(header("methodName").isEqualTo("purgeObject"))
                    .to("direct:fedora.delete")
                .otherwise()
                    .setHeader(HTTP_METHOD, constant("GET"))
                    .setHeader(HTTP_URI, simple("{{fcrepo.baseUrl}}{{fcrepo.basePath}}/object/${property[pid]}/objectXML"))
                    .log(DEBUG, LOGGER, "Getting foxml ${property[pid]}")
                    .to("http4://localhost?authUsername={{fcrepo.authUser}}&authPassword={{fcrepo.authPassword}}")
//                    .process(new Processor() {
//                        @Override
//                        public void process(final Exchange exchange) {
//                            final Document body = exchange.getIn().getBody(Document.class);
//                            exchange.getIn().setBody(body);
//                        }
//                    })
                    .choice()
                        .when(header(HTTP_RESPONSE_CODE).isEqualTo(200))
                            .to("direct:fedora.insert")
                        .otherwise()
                            .log(ERROR, LOGGER, "Unable to get {{fcrepo.baseUrl}}{{fcrepo.basePath}}/object/${property[pid]}/objectXML")
                    .endChoice()
            .end();

        from("direct:fedora.insert")
            .routeId("fedora-insert-multicaster")
            .description("Fedora Message insert multicaster")
            .log(TRACE, LOGGER, "Started fedora-insert-multicaster")
            .log(DEBUG, LOGGER, "aggregating ${property[pid]}")
            .choice()
                .when(xpath("/foxml:digitalObject/foxml:objectProperties/foxml:property[@NAME = 'info:fedora/fedora-system:def/model#state' and @VALUE = 'Active']"))
                    .multicast(stringConcatStrategy, false)
                        .to("direct:fedora.properties")
                            .split(body().tokenizeXML("datastream", "foxml"), stringConcatStrategy)
                            .streaming()
                            .to("direct:fedora.dsProcess")
                        .end()
                    .setBody(simple("<update><add><doc>${body}</doc></add></update>"))
                    .to("seda:solr.update?exchangePattern=InOnly")
                .endChoice()
            .otherwise()
                .to("direct:solr.delete")
            .end()
            .log(TRACE,  LOGGER, "Completed fedora-insert-multicaster");

        from("direct:fedora.properties")
            .routeId("fedora-foxml-properties")
            .description("Process the FOXML digitalObject XML to get some base Solr fields (including PID)")
            .log(TRACE, LOGGER, "Started fedora-foxml-properties")
            .setHeader("dsid", constant("FOXML"))
            .setHeader("pid", header("pid"))
            .setHeader("FEDORAUSER", exchangeProperty("fcrepo.authUser"))
            .setHeader("FEDORAPASS", exchangeProperty("fcrepo.authPassword"))
            .setHeader("FEDORAURL", exchangeProperty("fcrepo.baseUrl"))
            .setHeader("FEDORAPATH", exchangeProperty("fcrepo.basePath"))
            .log(DEBUG, LOGGER, "xslt is ${properties:xslt.path}")
            .to("xslt:{{xslt.path}}/FOXML.xslt?transformerFactoryClass=org.apache.xalan.processor.TransformerFactoryImpl")
            .log(TRACE, LOGGER, "Completed fedora-foxml-properties");

        from("direct:fedora.dsProcess")
            .routeId("fedora-ds-process")
            .description("Process the individual datastreams and return the Solr fields for their data.")
            .log(TRACE, LOGGER, "Started fedora-ds-process")
            .setHeader("DSID", xpath("/foxml:datastream/@ID"))
            .setHeader("mimetype", xpath("/foxml:datastream/foxml:datastreamVersion[last()]/@MIMETYPE"))
            .log(DEBUG, LOGGER, "DSID (${header[DSID]}), mimetype (${header[mimetype]})")
            .choice()
                .when(method("xsltExistsPredicate", "xsltExists"))
                    .choice()
                        .when(in(
                            header("mimetype").isEqualTo("text/xml"),
                            header("mimetype").isEqualTo("application/xml"),
                            header("mimetype").isEqualTo("application/rdf+xml"),
                            header("mimetype").isEqualTo("text/html")
                        ))
                            .to("direct:dsXML")
                        .when(header("mimetype").isEqualTo("text/plain"))
                            .to("direct:dsText")
                        .otherwise()
                            .setBody(constant(""))
                    .end()
                    .to("log:?logger=myLogger&level=TRACE")
                    .log(DEBUG, LOGGER, "Trying {{xslt.path}}/${header[DSID]}.xslt")
                    .recipientList(simple("xslt:{{xslt.path}}/${header[DSID]}.xslt?transformerFactoryClass=org.apache.xalan.processor.TransformerFactoryImpl"))
                    .endChoice()
               .otherwise()
                 .setBody(constant(""))
             .end()
             .log(TRACE, LOGGER, "Completed fedora-ds-process");

        from("direct:dsXML")
            .routeId("fedora-ds-isXML")
            .description("The datastream has an XML/HTML mimetype")
            .removeHeaders("CamelHttp*")
            .setHeader(HTTP_METHOD, constant("GET"))
            .setHeader(HTTP_URI, simple("{{fcrepo.baseUrl}}{{fcrepo.basePath}}/objects/${header[pid]}/datastreams/${header[DSID]}/content"))
            .log(DEBUG, LOGGER, "Getting XML datastream ${header[DSID]} for ${header[pid]}")
            .to("direct:get-url")
            .filter(body().isNotNull()).setBody(body().convertTo(Document.class));

        from("direct:dsText")
            .routeId("fedora-ds-isText")
            .description("The datastream has a text mimetype")
            .removeHeaders("CamelHttp*")
            .setHeader(HTTP_METHOD, constant("GET"))
            .setHeader(HTTP_URI, simple("{{fcrepo.baseUrl}}{{fcrepo.basePath}}/objects/${header[pid]}/datastreams/${header[DSID]}/content"))
            .log(DEBUG, LOGGER, "Getting Text datastream ${header[DSID]} for ${header[pid]}")
            .to("direct:get-url")
            .filter(body().isNotNull()).setBody(body().convertToString()).to("bean:StringUtils?method=convertToXML");


        from("direct:get-url")
            .routeId("fedora-get-url")
            .description("Get the HTTP_URI and check we got it, then return the message")
            .to("http4://localhost?authUsername={{fcrepo.authUser}}&authPassword={{fcrepo.authPassword}}")
            .choice()
                .when(not(header(HTTP_RESPONSE_CODE).isEqualTo(200)))
                    .log(WARN, LOGGER, "Problem getting ${header[CamelHttpUri]} received ${header[CamelHttpResponseCode]}")
                    .setBody(constant(""))
            .end();

        from("direct:fedora.delete")
            .routeId("fedora-delete-multicaster")
            .description("Fedora Message delete multicaster")
            .to("seda:solr.delete?exchangePattern=InOnly");

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
            .log(TRACE, LOGGER, "Started solr-insertion")
            .to("log:ca.umanitoba.dam.islandora.fc3indexer?level=TRACE")
            .setHeader("SolrOperation", constant(OPERATION_INSERT))
            .to("{{solr.baseUrl}}")
            .log(INFO,  LOGGER, "Added/Updated ${header[pid]} to Solr")
            .log(TRACE, LOGGER, "Completed solr-insertion");

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
