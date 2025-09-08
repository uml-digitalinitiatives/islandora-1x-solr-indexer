package ca.umanitoba.dam.islandora.fc3indexer;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.reifier.RouteReifier;
import org.apache.camel.test.spring.UseAdviceWith;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@UseAdviceWith
@ContextConfiguration(locations = {"classpath:fedoraSolrIndexer-context-test.xml"})
@RunWith(SpringJUnit4ClassRunner.class)
public class FedoraSolrIndexerTests {

    @Produce("direct:start")
    protected ProducerTemplate template;

    @Autowired
    protected CamelContext context;

    private final String modifyObjectBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<entry xmlns=\"http://www.w3.org/2005/Atom\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:fedora-types=\"http://www.fedora.info/definitions/1/0/types/\">\n" +
            "  <id>urn:uuid:1f27ab84-11d3-4838-9d5f-522641aec297</id>\n" +
            "  <updated>2025-09-08T19:28:41.897Z</updated>\n" +
            "  <author>\n" +
            "    <name>admin</name>\n" +
            "    <uri>http://localhost:8080/fedora</uri>\n" +
            "  </author>\n" +
            "  <title type=\"text\">modifyObject</title>\n" +
            "  <category term=\"PID\" scheme=\"fedora-types:pid\" label=\"xsd:string\"></category>\n" +
            "  <category term=\"null\" scheme=\"fedora-types:state\" label=\"xsd:string\"></category>\n" +
            "  <category term=\"null\" scheme=\"fedora-types:label\" label=\"xsd:string\"></category>\n" +
            "  <summary type=\"text\">islandora:10101</summary>\n" +
            "  <content type=\"text\">2025-09-08T19:28:41.897Z</content>\n" +
            "  <category term=\"3.8.2-SNAPSHOT\" scheme=\"info:fedora/fedora-system:def/view#version\"></category>\n" +
            "  <category term=\"info:fedora/fedora-system:ATOM-APIM-1.0\" scheme=\"http://www.fedora.info/definitions/1/0/types/formatURI\"></category>\n" +
            "</entry>";

    @Test
    public void testAggregation() throws Exception {
        final String route = "fedora-aggregator";

        final ModelCamelContext mcc = context.adapt(ModelCamelContext.class);
        RouteReifier.adviceWith(mcc.getRouteDefinition(route), mcc, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                replaceFromWith("direct:start");
                mockEndpointsAndSkip("direct:internal");

            }
        });

        final String modifyDatastreamBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<entry xmlns=\"http://www.w3.org/2005/Atom\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:fedora-types=\"http://www.fedora.info/definitions/1/0/types/\">\n" +
                "  <id>urn:uuid:d4a53607-68f8-427e-92ce-4ff47e196a72</id>\n" +
                "  <updated>2025-09-05T20:11:02.856Z</updated>\n" +
                "  <author>\n" +
                "    <name>some-user</name>\n" +
                "    <uri>http://localhost:8081/fedora</uri>\n" +
                "  </author>\n" +
                "  <title type=\"text\">modifyDatastreamByReference</title>\n" +
                "  <category term=\"PID\" scheme=\"fedora-types:pid\" label=\"xsd:string\"></category>\n" +
                "  <category term=\"DSID\" scheme=\"fedora-types:dsID\" label=\"xsd:string\"></category>\n" +
                "  <category term=\"\" scheme=\"fedora-types:altIDs\" label=\"fedora-types:ArrayOfString\"></category>\n" +
                "  <category term=\"null\" scheme=\"fedora-types:dsLabel\" label=\"xsd:string\"></category>\n" +
                "  <category term=\"null\" scheme=\"fedora-types:formatURI\" label=\"xsd:string\"></category>\n" +
                "  <category term=\"uploaded://3241\" scheme=\"fedora-types:dsLocation\" label=\"xsd:string\"></category>\n" +
                "  <category term=\"null\" scheme=\"fedora-types:checksumType\" label=\"xsd:string\"></category>\n" +
                "  <category term=\"null\" scheme=\"fedora-types:checksum\" label=\"xsd:string\"></category>\n" +
                "  <category term=\"null\" scheme=\"fedora-types:logMessage\" label=\"xsd:string\"></category>\n" +
                "  <summary type=\"text\">uofm:1248037</summary>\n" +
                "  <content type=\"text\">2025-09-05T20:11:02.856Z</content>\n" +
                "  <category term=\"3.8.2-SNAPSHOT\" scheme=\"info:fedora/fedora-system:def/view#version\"></category>\n" +
                "  <category term=\"info:fedora/fedora-system:ATOM-APIM-1.0\" scheme=\"http://www.fedora.info/definitions/1/0/types/formatURI\"></category>\n" +
                "</entry>";

        final MockEndpoint endpoint = context.getEndpoint("mock:direct:internal", MockEndpoint.class);
        endpoint.expectedMessageCount(2);
        endpoint.setResultWaitTime(5000);

        final Map<String, Object> header1 = Map.of("methodName", "ingest", "pid", "test:1234");
        final Map<String, Object> header2 = Map.of("methodName", "modifyDatastreamByReference", "pid", "test:1234");
        final Map<String, Object> header3 = Map.of("methodName", "ingest", "pid", "test:9999");

        template.sendBodyAndHeaders(modifyObjectBody.replace("PID", "test:1234"), header1);
        template.sendBodyAndHeaders(modifyDatastreamBody.replace("PID", "test:1234").replace("DSID", "TN"), header2);
        template.sendBodyAndHeaders(modifyObjectBody.replace("PID", "test:9999"), header3);

        endpoint.assertIsSatisfied();
    }

    @Test
    public void testAggregation2() throws Exception {
        final String route = "fedora-aggregator";
        final ModelCamelContext mcc = context.adapt(ModelCamelContext.class);
        RouteReifier.adviceWith(mcc.getRouteDefinition(route), mcc, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                replaceFromWith("direct:start");
                mockEndpointsAndSkip("direct:internal");
            }
        });

        final MockEndpoint endpoint = mcc.getEndpoint("mock:direct:internal", MockEndpoint.class);
        endpoint.expectedMessageCount(2);
        endpoint.setResultWaitTime(5000);

        template.sendBodyAndHeaders("direct:start", "<dummy/>", Map.of("methodName", "dummy"));
        template.sendBodyAndHeaders("direct:start", "<dummy/>", Map.of("methodName", "dummy", "pid", "test:1234"));
        template.sendBodyAndHeaders("direct:start", "<dummy/>", Map.of("methodName", "dummy", "pid", "test:1234"));
        template.sendBodyAndHeaders("direct:start", "<dummy/>", Map.of("methodName", "dummy", "pid", "test:101010"));

        endpoint.assertIsSatisfied();
    }

    @Test
    public void testRouting() throws Exception {
        final String route = "fedora-routing";

        final ModelCamelContext mcc = context.adapt(ModelCamelContext.class);
        RouteReifier.adviceWith(mcc.getRouteDefinition(route), mcc, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                replaceFromWith("direct:start");
                mockEndpointsAndSkip("direct:fedora.*");
            }
        });

        final Map<String, Object> header1 = Map.of("methodName", "ingest", "pid", "test:9999");
        final Map<String, Object> header2 = Map.of("methodName", "modifyDatastream", "pid", "test:9999");
        final Map<String, Object> header3 = Map.of("methodName", "purgeObject", "pid", "test:9999");

        ((MockEndpoint)context.getEndpoint("mock:direct:fedora.delete")).expectedMessageCount(1);
        ((MockEndpoint)context.getEndpoint("mock:direct:fedora.getObjectXml")).expectedMessageCount(2);

        template.sendBodyAndHeaders("<dummy />", header1);
        template.sendBodyAndHeaders("<dummy />", header2);
        template.sendBodyAndHeaders("<dummy />", header3);

        ((MockEndpoint)context.getEndpoint("mock:direct:fedora.delete")).assertIsSatisfied();
        ((MockEndpoint)context.getEndpoint("mock:direct:fedora.getObjectXml")).assertIsSatisfied();
    }

    @Test
    public void testRestConsumer() throws Exception {
        final String route = "rest-to-reindex";

        final ModelCamelContext mcc = context.adapt(ModelCamelContext.class);
        RouteReifier.adviceWith(mcc.getRouteDefinition(route), mcc, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpointsAndSkip("direct:internal");
            }
        });

        ((MockEndpoint) context.getEndpoint("mock:direct:internal")).expectedMessageCount(3);

        final String REST_ENDPOINT = "http://localhost:9222/fedora3-solr-indexer";
        template.requestBody(REST_ENDPOINT + "/reindex/testPID", null, String.class);
        template.requestBody(REST_ENDPOINT + "/reindex/testPID2", null, String.class);
        template.requestBody(REST_ENDPOINT + "/reindex/testPID3", null, String.class);

        ((MockEndpoint) context.getEndpoint("mock:direct:internal")).assertIsSatisfied();
    }

    @Test
    public void testSkipAudit() throws Exception {

        final String auditXmlBody = modifyObjectBody.replace(
                "  <category term=\"null\" scheme=\"fedora-types:label\" label=\"xsd:string\"></category>\n",
                "  <category term=\"null\" scheme=\"fedora-types:label\" label=\"xsd:string\"></category>\n" +
                "  <category term=\"PREMIS:file=islandora:9999+OBJ+OBJ.0; PREMIS:eventType=fixity check; PREMIS:eventOutcome=Checksums not enabled.\" scheme=\"fedora-types:logMessage\" label=\"xsd:string\"></category>\n"
        );
        final String otherBody = modifyObjectBody.replace("islandora:10101", "islandora:12345");

        final String route = "fedora-aggregator";

        final ModelCamelContext mcc = context.adapt(ModelCamelContext.class);
        RouteReifier.adviceWith(mcc.getRouteDefinition(route), mcc, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                replaceFromWith("direct:start");
                mockEndpointsAndSkip("direct:internal");
            }
        });

        final MockEndpoint endpoint = context.getEndpoint("mock:direct:internal", MockEndpoint.class);
        endpoint.expectedMessageCount(2);
        endpoint.setResultWaitTime(5000);

        template.sendBodyAndHeaders(modifyObjectBody, Map.of("methodName", "modifyObject", "pid", "islandora:10101"));
        template.sendBodyAndHeaders(auditXmlBody, Map.of("methodName", "modifyObject", "pid", "islandora:9999"));
        template.sendBodyAndHeaders(otherBody, Map.of("methodName", "modifyObject", "pid", "islandora:12345"));

        endpoint.assertIsSatisfied();
    }

}
