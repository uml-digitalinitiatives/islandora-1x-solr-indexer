package ca.umanitoba.dam.islandora.fc3indexer;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

/**
 * Holds/parses the properties file.
 * @author whikloj
 */
@PropertySources({
        @PropertySource(value = "file:${" + IndexerProps.DEFAULT_PROPERTY_FILE + "}", ignoreResourceNotFound = true),
        @PropertySource(value = "classpath:default.yml")
})
@Configuration
public class IndexerProps {
    public static final String DEFAULT_PROPERTY_FILE = "fc3indexer.config.file";
    protected static final String XSLT_PATH_PROP = "xslt.path";
    protected static final String MAX_ERROR_DELIVERIES = "error.maxRedeliveries";
    protected static final String JMS_BROKER = "jms.brokerUrl";
    protected static final String JMS_USERNAME = "jms.username";
    protected static final String JMS_PASSWORD = "jms.password";
    protected static final String JMS_PROCESSES = "jms.processes";
    protected static final String QUEUE_INCOMING = "queue.incoming";
    protected static final String QUEUE_INTERNAL = "queue.internal";
    protected static final String QUEUE_DEADLETTER = "queue.dead-letter";
    protected static final String FCREPO_BASEURL = "fcrepo.baseUrl";
    protected static final String FCREPO_BASEPATH = "fcrepo.basePath";
    protected static final String FCREPO_USERNAME = "fcrepo.authUser";
    protected static final String FCREPO_PASSWORD = "fcrepo.authPassword";
    protected static final String SOLR_BASEURL = "solr.baseUrl";
    protected static final String SOLR_PROCESSES = "solr.processes";
    protected static final String COMPLETION_TIMEOUT = "completion.timeout";
    protected static final String REINDEXER_PORT = "reindexer.port";
    protected static final String REINDEXER_PATH = "reindexer.path";

    protected static final String SPRING_RUN_CAMEL = "camel.springboot.main-run-controller";

    // Holds configuration properties to variables for later use in tests.
    protected final Map<String, String> prop2Method = new HashMap<>();

    @Value("${" + XSLT_PATH_PROP + ":}")
    private String xsltPath;

    @Value("${" + MAX_ERROR_DELIVERIES + ":3}")
    private int maxErrorDeliveries;

    @Value("${" + JMS_BROKER + ":tcp://localhost:61616}")
    private String jmsBroker;

    @Value("${" + JMS_USERNAME + ":}")
    private String jmsUsername;

    @Value("${" + JMS_PASSWORD + ":}")
    private String jmsPassword;

    @Value("${" + JMS_PROCESSES + ":1}")
    private int jmsProcesses;

    private static final Logger LOGGER = getLogger(IndexerProps.class);

    public String getJmsBroker() {
        return jmsBroker;
    }

    public String getJmsUsername() {
        return jmsUsername;
    }

    public String getJmsPassword() {
        return jmsPassword;
    }

    public int getJmsProcesses() {
        return jmsProcesses;
    }

}
