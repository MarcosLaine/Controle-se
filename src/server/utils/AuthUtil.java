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
        // NÃO confia no atributo cached - sempre valida o token novamente para garantir segurança
        // Isso previne problemas de reutilização de atributos entre requisições
        
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Token de autenticação ausente");
        }
        
        String token = authHeader.substring("Bearer ".length()).trim();
        
        // Valida se o token tem formato básico antes de tentar validar
        if (token.isEmpty()) {
            throw new UnauthorizedException("Token de autenticação vazio");
        }
        
        JwtUtil.JwtValidationResult result = JwtUtil.validateToken(token);
        
        if (!result.isValid()) {
            // Só loga como erro se não for apenas ausência de token (que é esperado em alguns casos)
            String errorMsg = result.getMessage();
            if (!errorMsg.contains("ausente") && !errorMsg.contains("vazio")) {
                System.err.println("[ERRO] AuthUtil.requireUserId: Token inválido - " + errorMsg);
            }
            throw new UnauthorizedException(errorMsg);
        }
        
        int userId = result.getUserId();
        // System.out.println("[DEBUG] AuthUtil.requireUserId: userId extraído do token: " + userId);
        if (userId <= 0) {
            System.err.println("[ERRO] AuthUtil.requireUserId: userId inválido: " + userId);
            throw new UnauthorizedException("UserId inválido extraído do token");
        }
        
        // Atualiza o atributo para cache (mas sempre valida o token primeiro)
        exchange.setAttribute("userId", userId);
        // System.out.println("[DEBUG] AuthUtil.requireUserId: userId=" + userId + " definido no atributo do exchange");
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

