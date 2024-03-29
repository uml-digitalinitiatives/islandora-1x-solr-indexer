package ca.umanitoba.dam.islandora.fc3indexer.utils;

import static org.apache.commons.text.StringEscapeUtils.ESCAPE_XML11;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

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

/**
 * Utility function for handling text datastreams.
 * 
 * @author whikloj
 */
public class StringToXmlProcessor implements Processor {

    private static Logger LOGGER = LoggerFactory.getLogger(StringToXmlProcessor.class);

    private CharSequenceTranslator translator;

    private String customFilePath;

    public StringToXmlProcessor(final String customFilePath) {
        this.customFilePath = customFilePath;
        setup();
    }

    private void setup() {
        final Map<CharSequence, CharSequence> lookupMap = new HashMap<>();
        if (customFilePath != null && !customFilePath.isEmpty()) {
            try {
                final URI fileUri;
                if (customFilePath.startsWith("classpath:")) {
                    fileUri = getClass().getClassLoader().getResource(customFilePath.substring(10)).toURI();
                } else {
                    fileUri = URI.create(customFilePath);
                }
                final File customFile = new File(fileUri);
                if (customFile.exists() && customFile.canRead()) {
                    Files.lines(customFile.toPath())
                            .filter(l -> !l.isBlank() && l.charAt(0) != '#' && l.contains(":"))
                            .peek(l -> LOGGER.debug("filtered line is {}", l))
                            .forEach(l -> {
                                final String[] parts = l.split(":");
                                if (parts.length == 2) {
                                    lookupMap.put(parts[0].trim(), parts[1].trim());
                                }
                            });
                }
            } catch (final URISyntaxException | IOException e) {
                throw new RuntimeException(e);
            }
        }
        final CharSequenceTranslator customRules = new LookupTranslator(lookupMap);
        translator = ESCAPE_XML11.with(customRules);
    }

    /**
     * Wrap the contents in an element named with the datastream ID and encode any necessary characters.
     * 
     * @param dsid The datastream ID we are acting on.
     * @param inputString The contents of that datastream, should be plain text.
     * @return The new XML Document.
     */
    private Document convertToXML(final String dsid, final String inputString) {
        LOGGER.debug("DSID is {} and body is {}", dsid, inputString.substring(0, Math.min(inputString.length(), 100)));
        if (translator == null) {
            setup();
        }
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
        final Document xmlDoc = convertToXML(inMsg.getHeader("DSID", String.class),
                inMsg.getBody(String.class));
        inMsg.setBody(xmlDoc);
    }
}
