/*
 * Copyright 2024 Astronuts, Inc., the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.astronuts.monitoring.logback.api;

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
