package io.astronuts.monitoring.api;

/**
 * Interface for transforming log events to a JSON format.
 */
public interface EventTransformer {

    /**
     * Transforms a log event to a JSON format.
     *
     * @param secretKey   The secret key to include in the JSON payload
     * @param eventObject The log event to transform
     * @return The log event in JSON format
     */
    String transformEventToJson(String secretKey, String eventObject);
}
