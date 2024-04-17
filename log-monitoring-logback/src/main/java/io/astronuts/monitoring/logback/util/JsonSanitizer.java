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

package io.astronuts.monitoring.logback.util;

/**
 * Utility class for sanitizing JSON strings.
 */
public class JsonSanitizer {

    /**
     * Constructor
     */
    public JsonSanitizer() {
    }

    /**
     * Sanitizes a JSON string by escaping control characters.
     * @param input the JSON string to sanitize
     * @return the sanitized JSON string
     */
    public static String sanitizeJsonString(String input) {
        if (input == null) {
            return null;
        }

        StringBuilder sanitized = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            switch (ch) {
                case '"':
                    sanitized.append("\\\"");
                    break;
                case '\\':
                    sanitized.append("\\\\");
                    break;
                case '\b':
                    sanitized.append("\\b");
                    break;
                case '\f':
                    sanitized.append("\\f");
                    break;
                case '\n':
                    sanitized.append("\\n");
                    break;
                case '\r':
                    sanitized.append("\\r");
                    break;
                case '\t':
                    sanitized.append("\\t");
                    break;
                default:
                    // Reference: http://www.unicode.org/versions/Unicode5.1.0/
                    if (ch <= 0x1F || ch >= 0x7F && ch <= 0x9F) {
                        sanitized.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sanitized.append(ch);
                    }
            }
        }
        return sanitized.toString();
    }
}

