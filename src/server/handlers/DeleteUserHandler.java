package server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import server.repository.UserRepository;
import server.utils.AuthUtil;
import server.utils.ResponseUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DeleteUserHandler implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(DeleteUserHandler.class.getName());
    private final UserRepository userRepository;

    public DeleteUserHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"DELETE".equals(exchange.getRequestMethod())) {
            ResponseUtil.sendErrorResponse(exchange, 405, "Método não permitido");
            return;
        }

        try {
            int userId = AuthUtil.requireUserId(exchange);
            userRepository.excluirUsuario(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Usuário excluído com sucesso");
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (AuthUtil.UnauthorizedException e) {
            ResponseUtil.sendErrorResponse(exchange, 401, e.getMessage());
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Usuário já havia sido removido");
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao excluir usuário", e);
            ResponseUtil.sendErrorResponse(exchange, 500, "Erro interno do servidor");
        }
    }
}

