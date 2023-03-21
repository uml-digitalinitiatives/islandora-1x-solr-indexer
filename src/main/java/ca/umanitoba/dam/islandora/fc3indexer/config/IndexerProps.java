package ca.umanitoba.dam.islandora.fc3indexer.config;

import static org.slf4j.LoggerFactory.getLogger;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.xml.XMLConstants;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.component.activemq.ActiveMQComponent;
import org.apache.camel.component.activemq.ActiveMQConfiguration;
import org.apache.camel.component.xslt.TransformerFactoryConfigurationStrategy;
import org.apache.camel.component.xslt.XsltEndpoint;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.xml.sax.EntityResolver;

import ca.umanitoba.dam.islandora.fc3indexer.utils.XHtmlCachingEntityResolver;

/**
 * Holds/parses the properties file.
 * @author whikloj
 */
@PropertySources({
        @PropertySource(value = "file:${" + IndexerProps.DEFAULT_PROPERTY_FILE + "}", ignoreResourceNotFound = true),
        @PropertySource(value = "classpath:default.properties")
})
@Configuration
public class IndexerProps implements TransformerFactoryConfigurationStrategy {
    private static final Logger LOGGER = getLogger(IndexerProps.class);

    public static final String DEFAULT_PROPERTY_FILE = "fc3indexer.config.file";

    protected static final String JMS_BROKER = "jms.brokerUrl";
    protected static final String JMS_USERNAME = "jms.username";
    protected static final String JMS_PASSWORD = "jms.password";
    protected static final String JMS_PROCESSES = "jms.processes";

    @Value("${" + JMS_BROKER + ":tcp://localhost:61616}")
    private String jmsBroker;

    @Value("${" + JMS_USERNAME + ":}")
    private String jmsUsername;

    @Value("${" + JMS_PASSWORD + ":}")
    private String jmsPassword;

    @Value("${" + JMS_PROCESSES + ":1}")
    private int jmsProcesses;

    @Bean("activemq")
    public ActiveMQComponent getActiveMQ() throws JMSException {
        return new ActiveMQComponent(getJmsConfig(jmsProcesses));
    }

    @Bean("ext-activemq")
    public ActiveMQComponent getExternalActiveMQ() throws JMSException {
        return new ActiveMQComponent(getJmsConfig(1));
    }

    @Bean
    public ConnectionFactory jmsConnectionFactory() throws JMSException {
        final var factory = new ActiveMQConnectionFactory();
        LOGGER.debug("jmsConnectionFactory: brokerUrl is {}", jmsBroker);
        if (!jmsBroker.isBlank()) {
            factory.setBrokerURL(jmsBroker);
            LOGGER.debug("jms username is {}", jmsUsername);
            if (!jmsUsername.isBlank() && !jmsPassword.isBlank()) {
                factory.createConnection(jmsUsername, jmsPassword);
            }
        }
        return factory;
    }

    private ActiveMQConfiguration getJmsConfig(final int consumers) throws JMSException {
        final var config = new ActiveMQConfiguration();
        if (!jmsBroker.isBlank()) {
            config.setBrokerURL(jmsBroker);
            if (!(jmsUsername.isBlank() || jmsPassword.isBlank())) {
                config.setUsername(jmsUsername);
                config.setPassword(jmsPassword);
            }
        }
        config.setConnectionFactory(jmsConnectionFactory());
        config.setConcurrentConsumers(consumers);
        return config;
    }

    @Override
    public void configure(final TransformerFactory factory, final XsltEndpoint endpoint) {
        LOGGER.debug("Configuring the XSLT Factory.");
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
        } catch (final TransformerConfigurationException e) {
            LOGGER.error("Unable to set XMLConstants.FEATURE_SECURE_PROCESSING to false");
        }
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "all");
    }

    @Bean(name = "cachingEntityResolver")
    public EntityResolver xhtmlEntityResolver() {
        return new XHtmlCachingEntityResolver();
    }
}
