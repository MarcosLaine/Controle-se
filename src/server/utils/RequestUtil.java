package server.utils;

import com.sun.net.httpserver.*;
import java.io.*;

/**
 * Utilitários para manipulação de requisições HTTP
 */
public class RequestUtil {
    
    /**
     * Lê o corpo da requisição HTTP
     */
    public static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), "UTF-8");
        }
    }
    
    /**
     * Extrai um parâmetro de query string
     * Caso especial: se paramName for "userId", tenta extrair do token JWT primeiro
     */
    public static String getQueryParam(HttpExchange exchange, String paramName) {
        if ("userId".equals(paramName)) {
            try {
                return String.valueOf(AuthUtil.requireUserId(exchange));
            } catch (AuthUtil.UnauthorizedException e) {
                // Se não conseguir do token, tenta do query param
            }
        }
        
        String query = exchange.getRequestURI().getQuery();
        if (query == null) return null;
        
        String[] params = query.split("&");
        for (String param : params) {
            String[] keyValue = param.split("=", 2); // Limit to 2 parts
            if (keyValue.length >= 1 && keyValue[0].equals(paramName)) {
                return keyValue.length == 2 ? keyValue[1] : "";
            }
        }
        return null;
    }
}

