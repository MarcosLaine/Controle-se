package server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import server.model.Usuario;
import server.repository.RefreshTokenRepository;
import server.repository.UserRepository;
import server.security.JwtUtil;
import server.utils.JsonUtil;
import server.utils.RequestUtil;
import server.utils.ResponseUtil;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RefreshTokenHandler implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(RefreshTokenHandler.class.getName());
    
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    
    public RefreshTokenHandler(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            ResponseUtil.sendErrorResponse(exchange, 405, "Método não permitido");
            return;
        }
        
        try {
            String requestBody = RequestUtil.readRequestBody(exchange);
            Map<String, String> data = JsonUtil.parseJson(requestBody);
            
            String refreshToken = data.get("refreshToken");
            if (refreshToken == null || refreshToken.isBlank()) {
                ResponseUtil.sendErrorResponse(exchange, 400, "Refresh token é obrigatório");
                return;
            }
            
            // Valida o refresh token e obtém o userId
            int userId = refreshTokenRepository.validateRefreshToken(refreshToken);
            if (userId <= 0) {
                ResponseUtil.sendErrorResponse(exchange, 401, "Refresh token inválido ou expirado");
                return;
            }
            
            // Busca o usuário
            Usuario usuario = userRepository.buscarUsuario(userId);
            if (usuario == null || !usuario.isAtivo()) {
                ResponseUtil.sendErrorResponse(exchange, 401, "Usuário não encontrado ou inativo");
                return;
            }
            
            // Implementa rotação de tokens: revoga o token antigo
            refreshTokenRepository.revokeToken(refreshToken);
            
            // Gera novo access token
            String newAccessToken = JwtUtil.generateAccessToken(usuario);
            
            // Gera novo refresh token
            String newRefreshToken = JwtUtil.generateRefreshToken();
            Instant expiresAt = Instant.now().plusSeconds(JwtUtil.getRefreshTokenExpirationSeconds());
            refreshTokenRepository.saveRefreshToken(userId, newRefreshToken, expiresAt);
            
            // Retorna os novos tokens
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("accessToken", newAccessToken);
            response.put("refreshToken", newRefreshToken);
            response.put("user", Map.of(
                "id", usuario.getIdUsuario(),
                "name", usuario.getNome(),
                "email", usuario.getEmail()
            ));
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao processar refresh token", e);
            ResponseUtil.sendErrorResponse(exchange, 500, "Erro interno do servidor");
        }
    }
}

