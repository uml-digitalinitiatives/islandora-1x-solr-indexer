package ca.umanitoba.dam.islandora.fc3indexer;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.reifier.RouteReifier;
import org.apache.camel.test.spring.CamelSpringBootRunner;
import org.apache.camel.test.spring.UseAdviceWith;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;


@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@UseAdviceWith
@ContextConfiguration(locations = {"/fedoraSolrIndexer-context-test.xml"})
@RunWith(CamelSpringBootRunner.class)
public class FedoraSolrIndexerTests {

    private static String REST_ENDPOINT = "http://localhost:9222/fedora3-solr-indexer";

    @Produce("direct:start")
    protected ProducerTemplate template;

    @Autowired
    protected CamelContext context;

    @Test
    public void testAggregation() throws Exception {
        final String route = "fedora-aggregator";

        final ModelCamelContext mcc = context.adapt(ModelCamelContext.class);
        RouteReifier.adviceWith(mcc.getRouteDefinition(route), mcc, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                replaceFromWith("direct:start");
                mockEndpointsAndSkip("queue.internal");

            }
        });
        context.start();

        final MockEndpoint endpoint = (MockEndpoint) context.getEndpoint("mock:queue.internal");

        final Map<String, Object> header1 = new HashMap<String, Object>();
        header1.put("methodName", "ingest");
        header1.put("pid", "test:1234");

        final Map<String, Object> header2 = new HashMap<String, Object>();
        header2.put("methodName", "modifyDatastreamByReference");
        header2.put("pid", "test:1234");

        final Map<String, Object> header3 = new HashMap<String, Object>();
        header3.put("methodName", "ingest");
        header3.put("pid", "test:9999");

        template.sendBodyAndHeaders("", header1);
        template.sendBodyAndHeaders("", header2);
        template.sendBodyAndHeaders("", header3);

        endpoint.assertIsSatisfied();
        context.stop();
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

        context.start();

        final Map<String, Object> header1 = new HashMap<String, Object>();
        header1.put("methodName", "ingest");
        header1.put("pid", "test:9999");

        final Map<String, Object> header2 = new HashMap<String, Object>();
        header2.put("methodName", "modifyDatastream");
        header2.put("pid", "test:9999");

        final Map<String, Object> header3 = new HashMap<String, Object>();
        header3.put("methodName", "purgeObject");
        header3.put("pid", "test:9999");

        ((MockEndpoint)context.getEndpoint("mock:direct:fedora.delete")).expectedMessageCount(1);
        ((MockEndpoint)context.getEndpoint("mock:direct:fedora.getObjectXml")).expectedMessageCount(2);

        template.sendBodyAndHeaders("", header1);
        template.sendBodyAndHeaders("", header2);
        template.sendBodyAndHeaders("", header3);

        ((MockEndpoint)context.getEndpoint("mock:direct:fedora.delete")).assertIsSatisfied();
        ((MockEndpoint)context.getEndpoint("mock:direct:fedora.getObjectXml")).assertIsSatisfied();
        context.stop();
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

        context.start();

        ((MockEndpoint) context.getEndpoint("mock:direct:internal")).expectedMessageCount(3);

        template.requestBody(REST_ENDPOINT + "/reindex/testPID", null, String.class);
        template.requestBody(REST_ENDPOINT + "/reindex/testPID2", null, String.class);
        template.requestBody(REST_ENDPOINT + "/reindex/testPID3", null, String.class);

        ((MockEndpoint) context.getEndpoint("mock:direct:internal")).assertIsSatisfied();
        context.stop();
    }

}
