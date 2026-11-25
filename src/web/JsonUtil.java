package web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Zero-dependency JSON parser and serializer supporting objects, arrays,
 * strings with escape sequences, numbers, booleans, and null.
 */
public final class JsonUtil {

    private JsonUtil() {
    }

    /**
     * Parses a JSON object string into a {@code Map<String, Object>}.
     *
     * @param json raw JSON text
     * @return parsed key-value map
     * @throws IllegalArgumentException if input is not a valid JSON object
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Request body must not be empty.");
        }
        Parser parser = new Parser(json);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.isAtEnd()) {
            throw new IllegalArgumentException("Unexpected trailing characters in JSON payload.");
        }
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Expected a JSON object.");
        }
        return (Map<String, Object>) value;
    }

    /**
     * Serializes a Java object graph (Maps, Lists, Strings, Numbers, Booleans, null) to JSON.
     */
    public static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        writeJson(value, sb);
        return sb.toString();
    }

    private static void writeJson(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeEscapedString(s, sb);
        } else if (value instanceof Number n) {
            if (value instanceof Double d && (d.isNaN() || d.isInfinite())) {
                sb.append("0.0");
            } else {
                sb.append(n);
            }
        } else if (value instanceof Boolean b) {
            sb.append(b ? "true" : "false");
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeEscapedString(String.valueOf(entry.getKey()), sb);
                sb.append(':');
                writeJson(entry.getValue(), sb);
            }
            sb.append('}');
        } else if (value instanceof Iterable<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeJson(item, sb);
            }
            sb.append(']');
        } else {
            writeEscapedString(String.valueOf(value), sb);
        }
    }

    private static void writeEscapedString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static final class Parser {
        private final String src;
        private int pos = 0;

        Parser(String src) {
            this.src = src;
        }

        boolean isAtEnd() {
            return pos >= src.length();
        }

        void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        Object parseValue() {
            skipWhitespace();
            if (isAtEnd()) {
                throw new IllegalArgumentException("Unexpected end of JSON input.");
            }
            char c = src.charAt(pos);
            return switch (c) {
                case '{' -> parseObjectInternal();
                case '[' -> parseArrayInternal();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> {
                    if (c == '-' || Character.isDigit(c)) {
                        yield parseNumber();
                    }
                    throw new IllegalArgumentException("Unexpected token '" + c + "' at position " + pos);
                }
            };
        }

        private Map<String, Object> parseObjectInternal() {
            pos++; // consume '{'
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (!isAtEnd() && src.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (isAtEnd() || src.charAt(pos) != '"') {
                    throw new IllegalArgumentException("Expected string key at position " + pos);
                }
                String key = parseString();
                skipWhitespace();
                if (isAtEnd() || src.charAt(pos) != ':') {
                    throw new IllegalArgumentException("Expected ':' after key at position " + pos);
                }
                pos++;
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (isAtEnd()) {
                    throw new IllegalArgumentException("Unterminated JSON object.");
                }
                char next = src.charAt(pos++);
                if (next == '}') {
                    return map;
                }
                if (next != ',') {
                    throw new IllegalArgumentException("Expected ',' or '}' at position " + (pos - 1));
                }
            }
        }

        private List<Object> parseArrayInternal() {
            pos++; // consume '['
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (!isAtEnd() && src.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (isAtEnd()) {
                    throw new IllegalArgumentException("Unterminated JSON array.");
                }
                char next = src.charAt(pos++);
                if (next == ']') {
                    return list;
                }
                if (next != ',') {
                    throw new IllegalArgumentException("Expected ',' or ']' at position " + (pos - 1));
                }
            }
        }

        private String parseString() {
            pos++; // consume opening '"'
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= src.length()) {
                        throw new IllegalArgumentException("Unterminated escape sequence.");
                    }
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"', '\\', '/' -> sb.append(esc);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > src.length()) {
                                throw new IllegalArgumentException("Invalid unicode escape.");
                            }
                            String hex = src.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        default -> throw new IllegalArgumentException("Invalid escape '\\" + esc + "'");
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string.");
        }

        private Boolean parseBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Invalid boolean at position " + pos);
        }

        private Object parseNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Invalid null literal at position " + pos);
        }

        private Number parseNumber() {
            int start = pos;
            if (src.charAt(pos) == '-') {
                pos++;
            }
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
            boolean isFloat = false;
            if (pos < src.length() && src.charAt(pos) == '.') {
                isFloat = true;
                pos++;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                    pos++;
                }
            }
            if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                isFloat = true;
                pos++;
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                    pos++;
                }
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                    pos++;
                }
            }
            String numStr = src.substring(start, pos);
            return isFloat ? Double.parseDouble(numStr) : Long.parseLong(numStr);
        }
    }
}
