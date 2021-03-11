package ca.umanitoba.dam.islandora.fc3indexer;

import static org.apache.commons.text.StringEscapeUtils.ESCAPE_XML11;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.commons.text.translate.CharSequenceTranslator;
import org.apache.commons.text.translate.LookupTranslator;
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
import java.util.HashMap;
import java.util.Map;

/**
 * Utility function for handling text datastreams.
 * 
 * @author whikloj
 */
public class StringToXmlProcessor implements Processor {

    private static Logger LOGGER = LoggerFactory.getLogger(StringToXmlProcessor.class);

    private static Map<CharSequence, CharSequence> lookupMap = new HashMap<>();
    static {
        lookupMap.put("\u2018", "");
    }
    private static CharSequenceTranslator customRules = new LookupTranslator(lookupMap);
    
    private static CharSequenceTranslator translator = ESCAPE_XML11.with(customRules);
    
    /**
     * Wrap the contents in an element named with the datastream ID and encode any necessary characters.
     * 
     * @param dsid The datastream ID we are acting on.
     * @param inputString The contents of that datastream, should be plain text.
     * @return The new XML Document.
     */
    public static Document convertToXML(final String dsid, final String inputString) {
        LOGGER.debug("DSID is {} and body is {}", dsid, Math.min(inputString.length(), 100));
        final String xmlVersion = translator.translate(inputString);
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
        } catch (final SAXException | IOException e) {
            LOGGER.error("Exception: ({}) {}", e.getClass(), e.getMessage());
        }
        return null;
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        final Message inMsg = exchange.getIn();
        final Document xmlDoc = StringToXmlProcessor.convertToXML(inMsg.getHeader("DSID", String.class),
                inMsg.getBody(String.class));
        inMsg.setBody(xmlDoc);
    }
}
