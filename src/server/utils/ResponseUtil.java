package server.utils;

import com.sun.net.httpserver.*;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/**
 * Utilitários para manipulação de respostas HTTP
 */
public class ResponseUtil {
    private static final int MIN_SIZE_FOR_COMPRESSION = 1024; // 1KB
    
    /**
     * Verifica se o cliente aceita compressão GZIP
     */
    private static boolean acceptsGzip(HttpExchange exchange) {
        String acceptEncoding = exchange.getRequestHeaders().getFirst("Accept-Encoding");
        return acceptEncoding != null && acceptEncoding.contains("gzip");
    }
    
    /**
     * Comprime dados usando GZIP
     */
    private static byte[] compressGzip(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(data);
        }
        return baos.toByteArray();
    }
    
    /**
     * Envia uma resposta JSON de sucesso com compressão opcional
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
        
        byte[] responseBytes = jsonResponse.getBytes("UTF-8");
        boolean shouldCompress = acceptsGzip(exchange) && responseBytes.length >= MIN_SIZE_FOR_COMPRESSION;
        
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        
        if (shouldCompress) {
            byte[] compressed = compressGzip(responseBytes);
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            exchange.sendResponseHeaders(statusCode, compressed.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(compressed);
            }
        } else {
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }
    
    /**
     * Envia uma resposta de erro com compressão opcional
     */
    public static void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        String jsonResponse = JsonUtil.toJson(response);
        
        byte[] responseBytes = jsonResponse.getBytes("UTF-8");
        boolean shouldCompress = acceptsGzip(exchange) && responseBytes.length >= MIN_SIZE_FOR_COMPRESSION;
        
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        
        if (shouldCompress) {
            byte[] compressed = compressGzip(responseBytes);
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            exchange.sendResponseHeaders(statusCode, compressed.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(compressed);
            }
        } else {
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }
}

