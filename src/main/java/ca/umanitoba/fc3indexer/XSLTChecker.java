package ca.umanitoba.fc3indexer;

import java.io.File;
import org.apache.camel.Handler;
import org.apache.camel.Header;
import org.apache.camel.language.Simple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XSLTChecker {

    private static Logger LOGGER = LoggerFactory.getLogger(XSLTChecker.class);

    @Handler
    public boolean xsltExists(@Header("DSID") final String dsid,
            @Simple(value = "${properties:foxmlXslt.path}") final String xsltDir) {
        LOGGER.trace("DSID ({}) and xslt path ({})", dsid, xsltDir);
        String tmpFile = xsltDir;
        if (tmpFile.startsWith("file://")) {
            tmpFile = tmpFile.replace("file://", "");
        }
        if (tmpFile.charAt(tmpFile.length() - 1) != '/') {
            tmpFile = tmpFile + "/";
        }
        tmpFile = tmpFile + dsid + ".xslt";
        LOGGER.trace("tmpFile is now {}", tmpFile);
        final File xsltFile = new File(tmpFile);
        final Boolean exists = xsltFile.exists();
        LOGGER.trace("returning {}", (exists ? "true" : "false"));
        return exists;
    }

}
