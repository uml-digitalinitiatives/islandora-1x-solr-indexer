package ca.umanitoba.dam.islandora.fc3indexer;

import java.util.Properties;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;


public class XSLTCheckerTest extends CamelTestSupport {

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return true;
    }

    // @Override
    // protected String getBlueprintDescriptor() {
    // return "OSGI-INF/blueprint/blueprint-test.xml";
    // }
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
                from("direct:start").filter(method(XSLTChecker.class, "exists")).to("mock:result");
            }
        };
    }

    @Test
    public void testExistingXSLT() throws Exception {
        context.start();
        MockEndpoint testEnd = getMockEndpoint("mock:result");
        testEnd.expectedMessageCount(2);
        template.sendBodyAndHeader("direct:start", "", "DSID", "FOXML");
        template.sendBodyAndHeader("direct:start", "", "DSID", "MODS");
        template.sendBodyAndHeader("direct:start", "", "DSID", "TIFF");
        template.sendBodyAndHeader("direct:start", "", "DSID", "RELS-EXT");
        testEnd.assertIsSatisfied();
        context.stop();
    }
}
