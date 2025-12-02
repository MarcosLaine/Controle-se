package server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import server.utils.ResponseUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class HealthHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            ResponseUtil.sendErrorResponse(exchange, 405, "Método não permitido");
            return;
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("timestamp", System.currentTimeMillis());
        ResponseUtil.sendJsonResponse(exchange, 200, response);
    }
}

