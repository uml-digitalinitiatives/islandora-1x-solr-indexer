package ca.umanitoba.dam.islandora.fc3indexer;

import java.io.File;
import java.net.URL;

import org.apache.camel.Header;
import org.apache.camel.language.simple.Simple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Checks for the existence of a stylesheet.
 * 
 * @author whikloj
 */
@Component
public class XSLTChecker {

    private static Logger LOGGER = LoggerFactory.getLogger(XSLTChecker.class);

    /**
     * Does the stylesheet exist?
     *
     * @param dsid The datastream ID we are acting on.
     * @param xsltDir The directory containing all our XSLTs
     * @return boolean Whether we found the XSLT.
     */
    public boolean exists(@Header("DSID") final String dsid, @Simple("${properties:xslt.path}") final String xsltDir) {
        String tmpFile = xsltDir;

        if (tmpFile.startsWith("file://")) {
            tmpFile = tmpFile.replace("file://", "");
        }
        if (tmpFile.charAt(tmpFile.length() - 1) != '/') {
            tmpFile = tmpFile + "/";
        }
        tmpFile = tmpFile + dsid + ".xslt";
        if (tmpFile.startsWith("classpath:")) {
            tmpFile = tmpFile.replace("classpath:", "");
            final URL resource = getClass().getClassLoader().getResource(tmpFile);
            if (resource != null) {
                tmpFile = resource.getPath().toString();
            }
        }
        final File xsltFile = new File(tmpFile);
        final Boolean exists = xsltFile.exists();
        LOGGER.debug("DSID ({}) and path ({}), returning {}", dsid, xsltDir, (exists ? "true" : "false"));
        return exists;
    }

}
