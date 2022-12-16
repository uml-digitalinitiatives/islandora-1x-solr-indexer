package ca.umanitoba.dam.islandora.fc3indexer.utils;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * A dumb cache of the XHTML1 transitional DTD to stop the indexer from constantly getting it for all HOCR.
 * @author whikloj
 */
public class XHtmlCachingEntityResolver implements EntityResolver {

    private static final Logger LOGGER = getLogger(XHtmlCachingEntityResolver.class);
    private static final Map<String, String> XHTML_PUBLIC_IDS = Map.of(
            "-//W3C//DTD XHTML 1.0 Transitional//EN", "xhtml1-transitional.dtd",
            "-//W3C//ENTITIES Latin 1 for XHTML//EN", "xhtml-lat1.ent",
            "-//W3C//ENTITIES Symbols for XHTML//EN", "xhtml-symbol.ent",
            "-//W3C//ENTITIES Special for XHTML//EN", "xhtml-special.ent"
    );

    @Override
    public InputSource resolveEntity(final String publicId, final String systemId) throws SAXException, IOException {
        LOGGER.trace("publicId is {}", publicId);
        if (XHTML_PUBLIC_IDS.containsKey(publicId)) {
            LOGGER.debug("Found publicId matching {}, returning cached source", publicId);
            return new InputSource(getClass().getClassLoader().getResourceAsStream(XHTML_PUBLIC_IDS.get(publicId)));
        }
        return null;
    }
}
