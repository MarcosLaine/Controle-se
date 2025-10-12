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
            return new String(is.readAllBytes());
        }
    }
    
    /**
     * Extrai um parâmetro de query string
     */
    public static String getQueryParam(HttpExchange exchange, String paramName) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) return null;
        
        String[] params = query.split("&");
        for (String param : params) {
            String[] keyValue = param.split("=");
            if (keyValue.length == 2 && keyValue[0].equals(paramName)) {
                return keyValue[1];
            }
        }
        return null;
    }
}

