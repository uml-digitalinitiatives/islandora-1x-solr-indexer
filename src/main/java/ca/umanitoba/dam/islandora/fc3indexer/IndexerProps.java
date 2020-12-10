package ca.umanitoba.dam.islandora.fc3indexer;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

/**
 * Holds/parses the properties file.
 * @author whikloj
 */
@PropertySources({
        @PropertySource(value = IndexerProps.DEFAULT_PROPERTY_FILE, ignoreResourceNotFound = true)
})
@Configuration
public class IndexerProps {
    static final String DEFAULT_PROPERTY_FILE = "file:${fc3indexer.config.file}";
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

    // Holds configuration properties to variables for later use in tests.
    protected final Map<String, String> prop2Method = new HashMap<>();

    @Value("${" + XSLT_PATH_PROP + ":}")
    private String xsltPath;

    @Value("${" + MAX_ERROR_DELIVERIES + ":3}")
    private String maxErrorDeliveries;

    @Value("${" + JMS_BROKER + ":tcp://localhost:61616}")
    private String jmsBroker;

    @Value("${" + JMS_USERNAME + ":}")
    private String jmsUsername;

    @Value("${" + JMS_PASSWORD + ":}")
    private String jmsPassword;

    @Value("${" + JMS_PROCESSES + ":1}")
    private String jmsProcesses;

    @Value("${" + QUEUE_INCOMING + ":direct:incoming}")
    private String queueIncoming;

    @Value("${" + QUEUE_INTERNAL + ":direct:internal}")
    private String queueInternal;

    @Value("${" + QUEUE_DEADLETTER + ":direct:trash}")
    private String queueDeadletter;

    @Value("${" + FCREPO_BASEURL + ":http://localhost:8080}")
    private String fcrepoBaseurl;

    @Value("${" + FCREPO_BASEPATH + ":/fedora}")
    private String fcrepoBasepath;

    @Value("${" + FCREPO_USERNAME + "}")
    private String fcrepoUsername;

    @Value("${" + FCREPO_PASSWORD + "}")
    private String fcrepoPassword;

    @Value("${" + SOLR_BASEURL + ":solr://localhost:8080/solr}")
    private String solrBaseurl;

    @Value("${" + SOLR_PROCESSES + ":1}")
    private String solrProcesses;

    @Value("${" + COMPLETION_TIMEOUT + ":10000}")
    private String completionTimeout;

    @Value("${" + REINDEXER_PORT + ":9111}")
    private String reindexerPort;

    @Value("${" + REINDEXER_PATH + ":/fedora3-solr-indexer}")
    private String reindexerPath;

    @PostConstruct
    public void setup() {
        prop2Method.putAll(
                Map.of(
                        XSLT_PATH_PROP, xsltPath,
                        MAX_ERROR_DELIVERIES, maxErrorDeliveries,
                        JMS_BROKER, jmsBroker,
                        JMS_USERNAME, jmsUsername,
                        JMS_PASSWORD, jmsPassword,
                        JMS_PROCESSES, jmsProcesses,
                        QUEUE_INCOMING, queueIncoming,
                        QUEUE_INTERNAL, queueInternal,
                        QUEUE_DEADLETTER, queueDeadletter
                )
        );
        prop2Method.putAll(
                Map.of(
                        FCREPO_BASEURL, fcrepoBaseurl,
                        FCREPO_BASEPATH, fcrepoBasepath,
                        FCREPO_USERNAME, fcrepoUsername,
                        FCREPO_PASSWORD, fcrepoPassword,
                        SOLR_BASEURL, solrBaseurl,
                        SOLR_PROCESSES, solrProcesses,
                        COMPLETION_TIMEOUT, completionTimeout,
                        REINDEXER_PATH, reindexerPath,
                        REINDEXER_PORT, reindexerPort
                )
        );
    }
    public String getXsltPath() {
        return xsltPath;
    }

    public void setXsltPath(final String xsltPath) {
        this.xsltPath = xsltPath;
    }

    public String getMaxErrorDeliveries() {
        return maxErrorDeliveries;
    }

    public void setMaxErrorDeliveries(final String maxErrorDeliveries) {
        this.maxErrorDeliveries = maxErrorDeliveries;
    }

    public String getJmsBroker() {
        return jmsBroker;
    }

    public void setJmsBroker(final String jmsBroker) {
        this.jmsBroker = jmsBroker;
    }

    public String getJmsUsername() {
        return jmsUsername;
    }

    public void setJmsUsername(final String jmsUsername) {
        this.jmsUsername = jmsUsername;
    }

    public String getJmsPassword() {
        return jmsPassword;
    }

    public void setJmsPassword(final String jmsPassword) {
        this.jmsPassword = jmsPassword;
    }

    public String getJmsProcesses() {
        return jmsProcesses;
    }

    public void setJmsProcesses(final String jmsProcesses) {
        this.jmsProcesses = jmsProcesses;
    }

    public String getQueueIncoming() {
        return queueIncoming;
    }

    public void setQueueIncoming(final String queueIncoming) {
        this.queueIncoming = queueIncoming;
    }

    public String getQueueInternal() {
        return queueInternal;
    }

    public void setQueueInternal(final String queueInternal) {
        this.queueInternal = queueInternal;
    }

    public String getQueueDeadletter() {
        return queueDeadletter;
    }

    public void setQueueDeadletter(final String queueDeadletter) {
        this.queueDeadletter = queueDeadletter;
    }

    public String getFcrepoBaseurl() {
        return fcrepoBaseurl;
    }

    public void setFcrepoBaseurl(final String fcrepoBaseurl) {
        this.fcrepoBaseurl = fcrepoBaseurl;
    }

    public String getFcrepoBasepath() {
        return fcrepoBasepath;
    }

    public void setFcrepoBasepath(final String fcrepoBasepath) {
        this.fcrepoBasepath = fcrepoBasepath;
    }

    public String getFcrepoUsername() {
        return fcrepoUsername;
    }

    public void setFcrepoUsername(final String fcrepoUsername) {
        this.fcrepoUsername = fcrepoUsername;
    }

    public String getFcrepoPassword() {
        return fcrepoPassword;
    }

    public void setFcrepoPassword(final String fcrepoPassword) {
        this.fcrepoPassword = fcrepoPassword;
    }

    public String getSolrBaseurl() {
        return solrBaseurl;
    }

    public void setSolrBaseurl(final String solrBaseurl) {
        this.solrBaseurl = solrBaseurl;
    }

    public String getSolrProcesses() {
        return solrProcesses;
    }

    public void setSolrProcesses(final String solrProcesses) {
        this.solrProcesses = solrProcesses;
    }

    public String getCompletionTimeout() {
        return completionTimeout;
    }

    public void setCompletionTimeout(final String completionTimeout) {
        this.completionTimeout = completionTimeout;
    }

    public String getReindexerPort() {
        return reindexerPort;
    }

    public void setReindexerPort(final String reindexerPort) {
        this.reindexerPort = reindexerPort;
    }

    public String getReindexerPath() {
        return reindexerPath;
    }

    public void setReindexerPath(final String reindexerPath) {
        this.reindexerPath = reindexerPath;
    }

    public Properties getProperties() {
        final Properties props = new Properties();
        for (final Map.Entry<String, String> e : prop2Method.entrySet()) {
            props.put(e.getKey(), e.getValue());
        }
        return props;
    }
}
