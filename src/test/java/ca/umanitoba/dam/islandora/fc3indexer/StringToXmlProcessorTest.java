package ca.umanitoba.dam.islandora.fc3indexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.EndpointInject;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.javaconfig.SingleRouteCamelConfiguration;
import org.apache.camel.test.spring.CamelSpringRunner;
import org.apache.camel.test.spring.UseOverridePropertiesWithPropertiesComponent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = {StringToXmlProcessorTest.ContextConfig.class})
@RunWith(CamelSpringRunner.class)
public class StringToXmlProcessorTest extends AbstractJUnit4SpringContextTests {

    @Produce("direct:start")
    protected ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @UseOverridePropertiesWithPropertiesComponent
    public static Properties testProps() {
        final var props = new Properties();
        props.put("custom.character.file", "classpath:stringtoxml.properties");
        return props;
    }

    private static String asciiText = "dog cat dog cat mouse";
    private static String expectedAsciiText = "<FULL_TEXT>hog cat hog cat mouse</FULL_TEXT>";

    private static String unicodeText = "This is a Σ\nBut this is a Ω";
    private static String expectedUnicodeText = "<UNICODE_TEXT>This is a Z\nBut this is a Ω</UNICODE_TEXT>";

    @Test
    public void testText() throws InterruptedException {
        final List<String> expectedMessages = new ArrayList<>();
        expectedMessages.add(expectedAsciiText);
        expectedMessages.add(expectedUnicodeText);
        result.expectedBodiesReceived(expectedMessages);

        template.sendBodyAndHeaders(asciiText, Map.of("DSID", "FULL_TEXT"));
        template.sendBodyAndHeaders(unicodeText, Map.of("DSID", "UNICODE_TEXT"));

        result.assertIsSatisfied();
    }


    @Configuration
    public static class ContextConfig extends SingleRouteCamelConfiguration {

        private Processor stringtoxml = new StringToXmlProcessor("classpath:stringtoxml.properties");
        @Bean
        public RouteBuilder route() {
            return new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:start")
                            .process(stringtoxml)
                            .to("mock:result");
                }
            };
        }
    }
}
