package server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import server.repository.RefreshTokenRepository;
import server.repository.UserRepository;
import server.utils.AuthUtil;
import server.utils.JsonUtil;
import server.utils.RequestUtil;
import server.utils.ResponseUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChangePasswordHandler implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(ChangePasswordHandler.class.getName());
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public ChangePasswordHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = new RefreshTokenRepository();
    }
    
    public ChangePasswordHandler(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            ResponseUtil.sendErrorResponse(exchange, 405, "Método não permitido");
            return;
        }

        try {
            int userId = AuthUtil.requireUserId(exchange);
            String requestBody = RequestUtil.readRequestBody(exchange);
            Map<String, String> data = JsonUtil.parseJson(requestBody);

            String currentPassword = data.get("currentPassword");
            String newPassword = data.get("newPassword");

            if (currentPassword == null || currentPassword.isBlank()) {
                ResponseUtil.sendErrorResponse(exchange, 400, "Senha atual é obrigatória");
                return;
            }
            if (newPassword == null || newPassword.isBlank()) {
                ResponseUtil.sendErrorResponse(exchange, 400, "Nova senha é obrigatória");
                return;
            }

            userRepository.atualizarSenhaUsuario(userId, currentPassword, newPassword);
            
            // Revoga todos os refresh tokens do usuário por segurança (possível comprometimento)
            refreshTokenRepository.revokeAllUserTokens(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Senha atualizada com sucesso. Faça login novamente.");
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (AuthUtil.UnauthorizedException e) {
            ResponseUtil.sendErrorResponse(exchange, 401, e.getMessage());
        } catch (IllegalArgumentException e) {
            ResponseUtil.sendErrorResponse(exchange, 400, e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao atualizar senha", e);
            ResponseUtil.sendErrorResponse(exchange, 500, "Erro interno do servidor");
        }
    }
}

