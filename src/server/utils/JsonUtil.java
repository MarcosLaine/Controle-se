package server.utils;

import java.util.*;

/**
 * Utilitários para manipulação de JSON
 */
public class JsonUtil {
    
    /**
     * Converte um objeto para JSON
     */
    public static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + escapeJson((String) obj) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":");
                sb.append(toJson(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escapeJson(obj.toString()) + "\"";
    }
    
    /**
     * Escapa caracteres especiais em strings JSON
     */
    public static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    /**
     * Parser JSON robusto usando regex
     */
    public static Map<String, String> parseJson(String json) {
        Map<String, String> result = new HashMap<>();
        
        try {
            // Pattern to match "key": "value" or "key": value pairs
            // This handles strings with any characters including commas, colons, etc.
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"([^\"]*)\"|([^,}]+))");
            java.util.regex.Matcher matcher = pattern.matcher(json);
            
            while (matcher.find()) {
                String key = matcher.group(1);
                String value = matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
                
                if (value != null) {
                    result.put(key.trim(), value.trim());
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao fazer parse do JSON: " + e.getMessage());
            System.err.println("JSON recebido: " + json);
        }
        
        return result;
    }
}

