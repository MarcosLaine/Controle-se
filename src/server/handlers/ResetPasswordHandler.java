package server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import server.repository.UserRepository;
import server.utils.JsonUtil;
import server.utils.RequestUtil;
import server.utils.ResponseUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ResetPasswordHandler implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(ResetPasswordHandler.class.getName());

    private final UserRepository userRepository;

    public ResetPasswordHandler(UserRepository userRepository) {
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
            String token = data != null ? data.get("token") : null;
            String newPassword = data != null ? data.get("newPassword") : null;

            if (token == null || token.isBlank()) {
                ResponseUtil.sendErrorResponse(exchange, 400, "Token é obrigatório");
                return;
            }
            if (newPassword == null || newPassword.isBlank()) {
                ResponseUtil.sendErrorResponse(exchange, 400, "Nova senha é obrigatória");
                return;
            }

            userRepository.redefinirSenhaComToken(token.trim(), newPassword);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Senha redefinida com sucesso. Faça login com a nova senha.");
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (IllegalArgumentException e) {
            ResponseUtil.sendErrorResponse(exchange, 400, e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao redefinir senha", e);
            ResponseUtil.sendErrorResponse(exchange, 500, "Erro interno do servidor");
        }
    }
}
