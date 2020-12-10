package ca.umanitoba.dam.islandora.fc3indexer;

import java.util.Properties;

import org.apache.camel.EndpointInject;
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
import org.springframework.test.context.ContextConfiguration;

@RunWith(CamelSpringRunner.class)
@ContextConfiguration(classes = {XSLTCheckerTest.ContextConfig.class})
public class XSLTCheckerTest {

    @Produce("direct:start")
    protected ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @UseOverridePropertiesWithPropertiesComponent
    public static Properties testProps() {
        final var props = new Properties();
        props.put("xslt.path", "classpath:xslts");
        return props;
    }

    @Test
    public void testExistingXSLT() throws Exception {
        result.expectedMessageCount(2);
        template.sendBodyAndHeader("direct:start", "", "DSID", "FOXML");
        template.sendBodyAndHeader("direct:start", "", "DSID", "MODS");
        template.sendBodyAndHeader("direct:start", "", "DSID", "TIFF");
        template.sendBodyAndHeader("direct:start", "", "DSID", "RELS-EXT");
        result.assertIsSatisfied();
    }

    @Configuration
    public static class ContextConfig extends SingleRouteCamelConfiguration {
        @Bean
        public RouteBuilder route() {
            return new RouteBuilder() {
                public void configure() {
                    from("direct:start").filter(method(XSLTChecker.class, "exists")).to("mock:result");
                }
            };
        }
    }
}
