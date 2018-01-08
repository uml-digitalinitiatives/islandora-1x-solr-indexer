package ca.umanitoba.dam.islandora.fc3indexer;

import static org.slf4j.LoggerFactory.getLogger;
import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.naming.Context;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.apache.camel.util.jndi.JndiContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.junit.Test;
import org.slf4j.Logger;

public class FedoraSolrIndexerTests extends CamelBlueprintTestSupport {

    private static Logger LOGGER = getLogger(FedoraSolrIndexerTests.class);

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected String getBlueprintDescriptor() {
        return "OSGI-INF/blueprint/blueprint-test.xml";
    }

    @Test
    public void testAggregation() throws Exception {
        final String route = "fedora-aggregator";

        context.getRouteDefinition(route).adviceWith(context,
            new AdviceWithRouteBuilder() {

                @Override
                public void configure() throws Exception {
                    replaceFromWith("direct:start");
                    mockEndpointsAndSkip("direct:internal");
                }
            });

        context.start();

        getMockEndpoint("mock:direct:internal").expectedMessageCount(2);

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

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRouting() throws Exception {
        final String route = "fedora-routing";

        context.getRouteDefinition(route).adviceWith(context,
            new AdviceWithRouteBuilder() {

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

        getMockEndpoint("mock:direct:fedora.delete").expectedMessageCount(1);
        getMockEndpoint("mock:direct:fedora.getObjectXml").expectedMessageCount(2);

        template.sendBodyAndHeaders("", header1);
        template.sendBodyAndHeaders("", header2);
        template.sendBodyAndHeaders("", header3);

        assertMockEndpointsSatisfied();
        context.stop();
    }


    public void testRestConsumer() throws Exception {
        final String route = "rest-to-reindex";

        context.getRouteDefinition(route).adviceWith(context,
            new AdviceWithRouteBuilder() {

                @Override
                public void configure() throws Exception {
                    mockEndpointsAndSkip("direct:internal");
                }
            });

        context.start();

        getMockEndpoint("mock:direct:internal").expectedMessageCount(3);

        template.requestBody("http4://localhost:9111/reindex/testPID", null, String.class);
        template.requestBody("http4://localhost:9111/reindex/testPID2", null, String.class);
        template.requestBody("http4://localhost:9111/reindex/testPID3", null, String.class);

        assertMockEndpointsSatisfied();
        context.stop();
    }
}
