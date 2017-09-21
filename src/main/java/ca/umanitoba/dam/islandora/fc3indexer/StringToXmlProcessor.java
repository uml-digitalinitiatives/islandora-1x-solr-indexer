package ca.umanitoba.dam.islandora.fc3indexer;

import static org.apache.commons.lang3.StringEscapeUtils.escapeXml10;

import org.apache.camel.Body;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.DocumentBuilder;

import java.io.IOException;
import java.io.StringReader;

public class StringToXmlProcessor implements Processor {

    private static Logger LOGGER = LoggerFactory.getLogger(StringToXmlProcessor.class);

    public static Document convertToXML(final String dsid, final String inputString) {
        LOGGER.debug("DSID is {} and body is {}", dsid, Math.min(inputString.length(), 100));
        final String xmlVersion = escapeXml10(inputString);
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        LOGGER.trace("Now string is {}", xmlVersion);

        factory.setNamespaceAware(true);

        try {
            final String theElement =
                    "<" + dsid + ">" + xmlVersion + "</" + dsid + ">";
            LOGGER.trace("Now theElement is {}", theElement);
            final DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(theElement)));
        } catch (final ParserConfigurationException e) {
            LOGGER.error("Parser Exception: {}", e.getMessage());
        } catch (SAXException | IOException e) {
            LOGGER.error("Exception: ({}) {}", e.getClass(), e.getMessage());
        }
        return null;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Message inMsg = exchange.getIn();
        Document xmlDoc = StringToXmlProcessor.convertToXML(inMsg.getHeader("DSID", String.class), inMsg.getBody(String.class));
        inMsg.setBody(xmlDoc);
    }
}
