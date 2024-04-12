package io.astronuts.monitoring.api;

import io.astronuts.monitoring.util.JsonSanitizer;

public class DefaultEventTransformer implements EventTransformer {

    @Override
    public String transformEventToJson(String secretKey, String eventObject) {
        return "{\"logRecord\": \"" + JsonSanitizer.sanitizeJsonString(eventObject) + "\", \"secretFileKey\": \"" + secretKey + "\"}";
    }
}
