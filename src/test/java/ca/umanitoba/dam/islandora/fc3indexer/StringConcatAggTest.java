package ca.umanitoba.dam.islandora.fc3indexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class StringConcatAggTest extends CamelTestSupport {

	private final AggregationStrategy stringConcatStrategy = new StringConcatAggregator();
	
    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return true;
    }

    @Override
    public Properties useOverridePropertiesWithPropertiesComponent() {
        final Properties props = new Properties();
        props.put("xslt.path", "classpath:xslts");
        return props;
    }

    @Override
    public RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("direct:start")
                .multicast(stringConcatStrategy).to("direct:A", "direct:B").end()
                .to("mock:result");
                
                from("direct:A")
                .setBody().javaScript("request.body.substring(0,3)");
                
                from("direct:B")
                .setBody().javaScript("request.body.toUpperCase()");
            }
        };
    }
    
    @Test
    public void testStringAggregation() throws Exception {
        context.start();
        MockEndpoint testEnd = getMockEndpoint("mock:result");
        List<String> expectedMessages = new ArrayList<>();
        
        expectedMessages.add("ThiTHIS IS AN EXAMPLE");
        expectedMessages.add("WhoWHO DO YOU THINK YOU ARE"); 
        testEnd.expectedBodiesReceived(expectedMessages);
        
        template.sendBody("direct:start", "This is an example");
        template.sendBody("direct:start", "Who do you think you are");
        
        testEnd.assertIsSatisfied();
        context.stop();
    }

}
