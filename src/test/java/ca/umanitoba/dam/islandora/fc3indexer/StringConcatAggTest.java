package ca.umanitoba.dam.islandora.fc3indexer;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.javaconfig.SingleRouteCamelConfiguration;
import org.apache.camel.test.spring.CamelSpringRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import ca.umanitoba.dam.islandora.fc3indexer.utils.StringConcatAggregator;

@RunWith(CamelSpringRunner.class)
@ContextConfiguration(classes = {StringConcatAggTest.ContextConfig.class})
public class StringConcatAggTest extends AbstractJUnit4SpringContextTests {

    private static final AggregationStrategy stringConcatStrategy = new StringConcatAggregator();

    @Produce("direct:start")
    protected ProducerTemplate template;

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;
    
    @Test
    public void testStringAggregation() throws Exception {
        final List<String> expectedMessages = new ArrayList<>();
        
        expectedMessages.add("ThiTHIS IS AN EXAMPLE");
        expectedMessages.add("WhoWHO DO YOU THINK YOU ARE");
        resultEndpoint.expectedBodiesReceived(expectedMessages);
        
        template.sendBody("direct:start", "This is an example");
        template.sendBody("direct:start", "Who do you think you are");
        
        resultEndpoint.assertIsSatisfied();
    }

    @Configuration
    public static class ContextConfig extends SingleRouteCamelConfiguration {
        @Bean
        public RouteBuilder route() {
            return new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:start")
                            .multicast(stringConcatStrategy).to("direct:A", "direct:B").end()
                            .to("mock:result");

                    from("direct:A")
                            .setBody().groovy("request.body.substring(0,3)");

                    from("direct:B")
                            .setBody().groovy("request.body.toUpperCase()");
                }
            };
        }
    }
}
