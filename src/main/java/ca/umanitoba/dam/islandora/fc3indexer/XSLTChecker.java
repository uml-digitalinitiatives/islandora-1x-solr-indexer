package ca.umanitoba.dam.islandora.fc3indexer;

import java.io.File;
import org.apache.camel.Handler;
import org.apache.camel.Header;
import org.apache.camel.language.Simple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class XSLTChecker {

    private static Logger LOGGER = LoggerFactory.getLogger(XSLTChecker.class);

    @Handler
    public static boolean xsltExists(@Header("DSID") final String dsid,
        @Simple(value = "${properties:xslt.path}") final String xsltDir) {
        String tmpFile = xsltDir;
        if (tmpFile.startsWith("file://")) {
            tmpFile = tmpFile.replace("file://", "");
        }
        if (tmpFile.charAt(tmpFile.length() - 1) != '/') {
            tmpFile = tmpFile + "/";
        }
        tmpFile = tmpFile + dsid + ".xslt";
        final File xsltFile = new File(tmpFile);
        final Boolean exists = xsltFile.exists();
        LOGGER.debug("DSID {} and path {}, returning {}", dsid, xsltDir, (exists ? "true" : "false"));
        return exists;
    }

}
