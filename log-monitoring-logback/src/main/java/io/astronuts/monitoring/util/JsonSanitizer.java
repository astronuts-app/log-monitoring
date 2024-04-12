package io.astronuts.monitoring.util;

public class JsonSanitizer {

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

