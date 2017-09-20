package ca.umanitoba.dam.islandora.fc3indexer;

import static org.slf4j.LoggerFactory.getLogger;

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

public class IndexerRouteTests extends CamelBlueprintTestSupport {

    private static Logger LOGGER = getLogger(IndexerRouteTests.class);

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;

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
}
