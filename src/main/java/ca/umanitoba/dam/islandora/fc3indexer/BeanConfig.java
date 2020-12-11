package ca.umanitoba.dam.islandora.fc3indexer;

import static org.slf4j.LoggerFactory.getLogger;

import javax.inject.Inject;
import javax.xml.transform.TransformerFactory;

import org.apache.camel.component.activemq.ActiveMQComponent;
import org.apache.camel.component.activemq.ActiveMQConfiguration;
import org.apache.xalan.processor.TransformerFactoryImpl;
import org.slf4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defines the bean needs the indexer.
 * @author whikloj
 */
@Configuration
public class BeanConfig {

    private static final Logger LOGGER = getLogger(BeanConfig.class);

    @Inject
    private IndexerProps indexerProps;

    @Bean("activemq")
    public ActiveMQComponent getActiveMQ() {
        final var jms_process = indexerProps.getJmsProcesses();
        return new ActiveMQComponent(getJmsConfig(jms_process));
    }

    @Bean("ext-activemq")
    public ActiveMQComponent getExternalActiveMQ() {
        return new ActiveMQComponent(getJmsConfig(1));
    }

    private ActiveMQConfiguration getJmsConfig(final int consumers) {
        final var config = new ActiveMQConfiguration();
        final var brokerUrl = indexerProps.getJmsBroker();
        LOGGER.debug("brokerUrl is {}", brokerUrl);
        if (!brokerUrl.isBlank()) {
            config.setBrokerURL(brokerUrl);
            final var username = indexerProps.getJmsUsername();
            final var password = indexerProps.getJmsPassword();
            LOGGER.debug("jms username/password is {} / {}", username, password);
            if (!username.isBlank() && !password.isBlank()) {
                config.setUsername(username);
                config.setPassword(password);
            }
        }
        config.setConcurrentConsumers(consumers);
        return config;
    }

    @Bean("xsltTransformer")
    public TransformerFactory getXalan() {
        return new TransformerFactoryImpl();
    }
}
