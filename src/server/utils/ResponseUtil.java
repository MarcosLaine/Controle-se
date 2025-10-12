package server.utils;

import com.sun.net.httpserver.*;
import java.io.*;
import java.util.*;

/**
 * Utilitários para manipulação de respostas HTTP
 */
public class ResponseUtil {
    
    /**
     * Envia uma resposta JSON de sucesso
     */
    public static void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String jsonResponse;
        if (data instanceof Map && ((Map<?, ?>) data).containsKey("success")) {
            // Data já contém estrutura completa de resposta
            jsonResponse = JsonUtil.toJson(data);
        } else {
            // Envolve data em resposta de sucesso
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);
            jsonResponse = JsonUtil.toJson(response);
        }
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        
        exchange.sendResponseHeaders(statusCode, jsonResponse.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(jsonResponse.getBytes());
        }
    }
    
    /**
     * Envia uma resposta de erro
     */
    public static void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        String jsonResponse = JsonUtil.toJson(response);
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        
        exchange.sendResponseHeaders(statusCode, jsonResponse.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(jsonResponse.getBytes());
        }
    }
}

