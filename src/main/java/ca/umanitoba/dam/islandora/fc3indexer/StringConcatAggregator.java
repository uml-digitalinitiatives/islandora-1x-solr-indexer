package ca.umanitoba.dam.islandora.fc3indexer;

import org.apache.camel.Exchange;
import org.apache.camel.AggregationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tools for the camel Solr Indexer
 *
 * @author whikloj
 * @since 2015-12-11
 */
public class StringConcatAggregator implements AggregationStrategy {

    private static Logger LOGGER = LoggerFactory.getLogger(StringConcatAggregator.class);
    /**
     * Straight string concatenate of Exchanges
     *
     * @param oldExchange The old Exchange
     * @param newExchange The new Exchange
     * @return Exchange with the two exchanges concatenated together
     */
    @Override
    public Exchange aggregate(final Exchange oldExchange, final Exchange newExchange) {
        if (oldExchange == null) {
            return newExchange;
        }

        final String oldBody = oldExchange.getIn().getBody(String.class);
        final String newBody = newExchange.getIn().getBody(String.class);
        LOGGER.trace("oldBody -> {}", oldBody);
        LOGGER.trace("newBody -> {}", newBody);
        oldExchange.getIn().setBody(oldBody + newBody);
        return oldExchange;
    }
}
