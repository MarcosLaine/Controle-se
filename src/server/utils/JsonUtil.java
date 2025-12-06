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
        if (json == null || json.trim().isEmpty()) return result;
        
        try {
            // Parser manual mais robusto que lida com strings longas
            int i = 0;
            while (i < json.length()) {
                // Pula espaços e vírgulas
                while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == ',' || json.charAt(i) == '\n' || json.charAt(i) == '\t')) {
                    i++;
                }
                if (i >= json.length()) break;
                
                // Procura por chave (começa com aspas)
                if (json.charAt(i) == '"') {
                    int keyStart = i + 1;
                    int keyEnd = json.indexOf('"', keyStart);
                    if (keyEnd == -1) break;
                    
                    String key = json.substring(keyStart, keyEnd);
                    i = keyEnd + 1;
                    
                    // Pula espaços e dois pontos
                    while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == ':')) {
                        i++;
                    }
                    if (i >= json.length()) break;
                    
                    // Lê o valor
                    String value = null;
                    if (json.charAt(i) == '"') {
                        // String value - lê até a próxima aspas (não escapada)
                        int valueStart = i + 1;
                        int valueEnd = valueStart;
                        while (valueEnd < json.length()) {
                            if (json.charAt(valueEnd) == '"' && (valueEnd == valueStart || json.charAt(valueEnd - 1) != '\\')) {
                                break;
                            }
                            valueEnd++;
                        }
                        if (valueEnd < json.length()) {
                            value = json.substring(valueStart, valueEnd);
                            // Remove escapes
                            value = value.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
                            i = valueEnd + 1;
                        }
                    } else {
                        // Valor primitivo (número, boolean, null)
                        int valueStart = i;
                        while (i < json.length() && json.charAt(i) != ',' && json.charAt(i) != '}' && json.charAt(i) != ']') {
                            i++;
                        }
                        value = json.substring(valueStart, i).trim();
                    }
                    
                    if (value != null) {
                        result.put(key, value);
                    }
                } else {
                    i++;
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao fazer parse do JSON: " + e.getMessage());
            System.err.println("JSON recebido: " + json);
        }
        
        return result;
    }
    
    /**
     * Parse JSON com suporte a objetos aninhados
     * Retorna Map<String, Object> onde valores podem ser String, Number ou Map aninhado
     */
    public static Map<String, Object> parseJsonWithNested(String json) {
        Map<String, Object> result = new HashMap<>();
        if (json == null) return result;
        
        try {
            json = json.trim();
            if (json.startsWith("{")) {
                json = json.substring(1);
            }
            if (json.endsWith("}")) {
                json = json.substring(0, json.length() - 1);
            }
            
            // Parse manual simples para suportar objetos aninhados
            int i = 0;
            while (i < json.length()) {
                // Encontra próxima chave
                int keyStart = json.indexOf("\"", i);
                if (keyStart == -1) break;
                int keyEnd = json.indexOf("\"", keyStart + 1);
                if (keyEnd == -1) break;
                
                String key = json.substring(keyStart + 1, keyEnd);
                
                // Encontra valor após o ":"
                int colonIndex = json.indexOf(":", keyEnd);
                if (colonIndex == -1) break;
                
                i = colonIndex + 1;
                while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
                    i++;
                }
                
                // Determina o tipo de valor
                if (i >= json.length()) break;
                
                char firstChar = json.charAt(i);
                Object value = null;
                
                if (firstChar == '\"') {
                    // String value - lê até a próxima aspas não escapada
                    int valueStart = i + 1;
                    int valueEnd = valueStart;
                    while (valueEnd < json.length()) {
                        if (json.charAt(valueEnd) == '"' && (valueEnd == valueStart || json.charAt(valueEnd - 1) != '\\')) {
                            break;
                        }
                        valueEnd++;
                    }
                    if (valueEnd < json.length()) {
                        String rawValue = json.substring(valueStart, valueEnd);
                        // Desescapar caracteres especiais
                        value = rawValue.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
                        i = valueEnd + 1;
                    }
                } else if (firstChar == '{') {
                    // Nested object
                    int braceCount = 1;
                    int objStart = i;
                    i++;
                    while (i < json.length() && braceCount > 0) {
                        if (json.charAt(i) == '{') braceCount++;
                        else if (json.charAt(i) == '}') braceCount--;
                        i++;
                    }
                    String nestedJson = json.substring(objStart, i);
                    value = parseJsonWithNested(nestedJson);
                } else if (firstChar == '[') {
                    // Array - parse como List
                    int bracketCount = 1;
                    int arrayStart = i;
                    i++;
                    while (i < json.length() && bracketCount > 0) {
                        if (json.charAt(i) == '[') bracketCount++;
                        else if (json.charAt(i) == ']') bracketCount--;
                        i++;
                    }
                    String arrayJson = json.substring(arrayStart, i);
                    value = parseJsonArray(arrayJson);
                } else {
                    // Número ou boolean
                    int valueEnd = i;
                    while (valueEnd < json.length() && 
                           json.charAt(valueEnd) != ',' && 
                           json.charAt(valueEnd) != '}') {
                        valueEnd++;
                    }
                    String valueStr = json.substring(i, valueEnd).trim();
                    
                    // Tenta converter para número ou boolean
                    try {
                        String normalizedStr = NumberUtil.normalizeBrazilianNumber(valueStr);
                        if (normalizedStr.contains(".")) {
                            value = Double.parseDouble(normalizedStr);
                        } else {
                            value = Integer.parseInt(normalizedStr);
                        }
                    } catch (NumberFormatException e) {
                        // Tenta converter para boolean
                        if (valueStr.equalsIgnoreCase("true")) {
                            value = true;
                        } else if (valueStr.equalsIgnoreCase("false")) {
                            value = false;
                        } else if (valueStr.equalsIgnoreCase("null")) {
                            value = null;
                        } else {
                            value = valueStr;
                        }
                    }
                    
                    i = valueEnd;
                }
                
                if (value != null) {
                    result.put(key, value);
                }
                
                // Pula vírgula se houver
                while (i < json.length() && (json.charAt(i) == ',' || Character.isWhitespace(json.charAt(i)))) {
                    i++;
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao fazer parse do JSON aninhado: " + e.getMessage());
            System.err.println("JSON recebido: " + json);
            e.printStackTrace();
        }
        
        return result;
    }
    
    /**
     * Parse um array JSON para List<Object>
     */
    @SuppressWarnings("unchecked")
    public static List<Object> parseJsonArray(String json) {
        List<Object> result = new ArrayList<>();
        if (json == null) return result;
        
        try {
            json = json.trim();
            if (json.startsWith("[")) {
                json = json.substring(1);
            }
            if (json.endsWith("]")) {
                json = json.substring(0, json.length() - 1);
            }
            
            if (json.trim().isEmpty()) {
                return result;
            }
            
            // Parse manual dos elementos do array
            int i = 0;
            while (i < json.length()) {
                // Pula espaços
                while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
                    i++;
                }
                if (i >= json.length()) break;
                
                char firstChar = json.charAt(i);
                Object value = null;
                
                if (firstChar == '\"') {
                    // String value - lê até a próxima aspas não escapada
                    int valueStart = i + 1;
                    int valueEnd = valueStart;
                    while (valueEnd < json.length()) {
                        if (json.charAt(valueEnd) == '"' && (valueEnd == valueStart || json.charAt(valueEnd - 1) != '\\')) {
                            break;
                        }
                        valueEnd++;
                    }
                    if (valueEnd < json.length()) {
                        String rawValue = json.substring(valueStart, valueEnd);
                        // Desescapar caracteres especiais
                        value = rawValue.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
                        i = valueEnd + 1;
                    }
                } else if (firstChar == '{') {
                    // Nested object
                    int braceCount = 1;
                    int objStart = i;
                    i++;
                    while (i < json.length() && braceCount > 0) {
                        if (json.charAt(i) == '{') braceCount++;
                        else if (json.charAt(i) == '}') braceCount--;
                        i++;
                    }
                    String nestedJson = json.substring(objStart, i);
                    value = parseJsonWithNested(nestedJson);
                } else if (firstChar == '[') {
                    // Nested array
                    int bracketCount = 1;
                    int arrayStart = i;
                    i++;
                    while (i < json.length() && bracketCount > 0) {
                        if (json.charAt(i) == '[') bracketCount++;
                        else if (json.charAt(i) == ']') bracketCount--;
                        i++;
                    }
                    String nestedArrayJson = json.substring(arrayStart, i);
                    value = parseJsonArray(nestedArrayJson);
                } else {
                    // Número ou boolean
                    int valueEnd = i;
                    while (valueEnd < json.length() && 
                           json.charAt(valueEnd) != ',' && 
                           json.charAt(valueEnd) != ']') {
                        valueEnd++;
                    }
                    String valueStr = json.substring(i, valueEnd).trim();
                    
                    // Tenta converter para número
                    try {
                        String normalizedStr = NumberUtil.normalizeBrazilianNumber(valueStr);
                        if (normalizedStr.contains(".")) {
                            value = Double.parseDouble(normalizedStr);
                        } else {
                            value = Integer.parseInt(normalizedStr);
                        }
                    } catch (NumberFormatException e) {
                        if (valueStr.equalsIgnoreCase("true")) {
                            value = true;
                        } else if (valueStr.equalsIgnoreCase("false")) {
                            value = false;
                        } else if (valueStr.equalsIgnoreCase("null")) {
                            value = null;
                        } else {
                            value = valueStr;
                        }
                    }
                    
                    i = valueEnd;
                }
                
                if (value != null) {
                    result.add(value);
                }
                
                // Pula vírgula se houver
                while (i < json.length() && (json.charAt(i) == ',' || Character.isWhitespace(json.charAt(i)))) {
                    i++;
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao fazer parse do array JSON: " + e.getMessage());
            System.err.println("JSON recebido: " + json);
        }
        
        return result;
    }
}

