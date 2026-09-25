package com.script.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 工具（无第三方依赖）。
 * 支持对象、数组、字符串、数字、布尔与 null 的解析和序列化。
 */
public final class Json {
    private Json() {
    }

    /** 解析 JSON 文本，返回 Map / List / String / Number / Boolean / null。 */
    public static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.isEnd()) {
            throw parser.error("JSON 末尾存在多余内容");
        }
        return value;
    }

    /** 解析 JSON 文本并要求根节点为对象。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("JSON 根节点必须是对象");
        }
        return (Map<String, Object>) value;
    }

    /** 将 Map / List / String / Number / Boolean / null 序列化为 JSON 文本。 */
    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(value, sb);
        return sb.toString();
    }

    private static void writeValue(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeString((String) value, sb);
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(String.valueOf(entry.getKey()), sb);
                sb.append(':');
                writeValue(entry.getValue(), sb);
            }
            sb.append('}');
        } else if (value instanceof Collection) {
            sb.append('[');
            boolean first = true;
            for (Object item : (Collection<?>) value) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeValue(item, sb);
            }
            sb.append(']');
        } else {
            writeString(String.valueOf(value), sb);
        }
    }

    private static void writeString(String text, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    /** 递归下降解析器。 */
    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text == null ? "" : text;
        }

        boolean isEnd() {
            return pos >= text.length();
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + "（位置 " + pos + "）");
        }

        void skipWhitespace() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object parseValue() {
            skipWhitespace();
            if (isEnd()) {
                throw error("内容意外结束");
            }
            char c = text.charAt(pos);
            switch (c) {
                case '{': return parseObject();
                case '[': return parseArray();
                case '"': return parseString();
                case 't': expect("true");  return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null");  return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return parseNumber();
                    }
                    throw error("无法识别的内容");
            }
        }

        private void expect(String word) {
            if (!text.startsWith(word, pos)) {
                throw error("期望 " + word);
            }
            pos += word.length();
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // 跳过 {
            skipWhitespace();
            if (!isEnd() && text.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (isEnd() || text.charAt(pos) != '"') {
                    throw error("对象的键必须是字符串");
                }
                String key = parseString();
                skipWhitespace();
                if (isEnd() || text.charAt(pos) != ':') {
                    throw error("对象缺少冒号");
                }
                pos++;
                map.put(key, parseValue());
                skipWhitespace();
                if (isEnd()) {
                    throw error("对象未闭合");
                }
                char c = text.charAt(pos);
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == '}') {
                    pos++;
                    break;
                }
                throw error("对象中期望逗号或右花括号");
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // 跳过 [
            skipWhitespace();
            if (!isEnd() && text.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (isEnd()) {
                    throw error("数组未闭合");
                }
                char c = text.charAt(pos);
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == ']') {
                    pos++;
                    break;
                }
                throw error("数组中期望逗号或右方括号");
            }
            return list;
        }

        private String parseString() {
            StringBuilder sb = new StringBuilder();
            pos++; // 跳过起始引号
            while (true) {
                if (isEnd()) {
                    throw error("字符串未闭合");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    break;
                }
                if (c == '\\') {
                    if (isEnd()) {
                        throw error("转义字符不完整");
                    }
                    char esc = text.charAt(pos++);
                    switch (esc) {
                        case '"':  sb.append('"');  break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/');  break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > text.length()) {
                                throw error("unicode 转义不完整");
                            }
                            try {
                                sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                            } catch (NumberFormatException e) {
                                throw error("非法的 unicode 转义");
                            }
                            pos += 4;
                            break;
                        default:
                            throw error("非法转义字符: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Number parseNumber() {
            int start = pos;
            if (text.charAt(pos) == '-') {
                pos++;
            }
            while (!isEnd() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
            boolean floating = false;
            if (!isEnd() && text.charAt(pos) == '.') {
                floating = true;
                pos++;
                while (!isEnd() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
            }
            if (!isEnd() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
                floating = true;
                pos++;
                if (!isEnd() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                    pos++;
                }
                while (!isEnd() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
            }
            String num = text.substring(start, pos);
            try {
                return floating ? (Number) Double.valueOf(num) : (Number) Long.valueOf(num);
            } catch (NumberFormatException e) {
                throw error("非法数字: " + num);
            }
        }
    }
}
