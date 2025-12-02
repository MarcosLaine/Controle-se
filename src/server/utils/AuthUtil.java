package server.utils;

import com.sun.net.httpserver.HttpExchange;
import server.security.JwtUtil;

/**
 * Utilitários para autenticação e autorização
 */
public class AuthUtil {
    
    /**
     * Extrai e valida o userId do token JWT na requisição
     * @throws UnauthorizedException se o token for inválido ou ausente
     */
    public static int requireUserId(HttpExchange exchange) {
        Object attr = exchange.getAttribute("userId");
        if (attr instanceof Integer) {
            return (Integer) attr;
        }
        
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Token de autenticação ausente");
        }
        
        String token = authHeader.substring("Bearer ".length()).trim();
        JwtUtil.JwtValidationResult result = JwtUtil.validateToken(token);
        
        if (!result.isValid()) {
            throw new UnauthorizedException(result.getMessage());
        }
        
        int userId = result.getUserId();
        exchange.setAttribute("userId", userId);
        return userId;
    }
    
    /**
     * Exceção para erros de autenticação/autorização
     */
    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }
}

