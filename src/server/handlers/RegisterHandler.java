package server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import server.model.Usuario;
import server.repository.UserRepository;
import server.security.JwtUtil;
import server.utils.JsonUtil;
import server.utils.RequestUtil;
import server.utils.ResponseUtil;
import server.validation.InputValidator;
import server.validation.ValidationResult;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RegisterHandler implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(RegisterHandler.class.getName());
    
    private final UserRepository userRepository;

    public RegisterHandler(UserRepository userRepository) {
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
            
            String name = data.get("name");
            String email = data.get("email");
            String password = data.get("password");
            
            ValidationResult validation = new ValidationResult();
            validation.addErrors(InputValidator.validateName("Nome", name, true).getErrors());
            validation.addErrors(InputValidator.validateEmail(email, true).getErrors());
            validation.addErrors(InputValidator.validatePassword(password, true).getErrors());
            
            if (!validation.isValid()) {
                ResponseUtil.sendErrorResponse(exchange, 400, validation.getErrorMessage());
                return;
            }
            
            name = InputValidator.sanitizeInput(name);
            String emailNormalizado = email.toLowerCase().trim();
            
            try {
                int userId = userRepository.cadastrarUsuario(name, emailNormalizado, password);
                
                // Tenta buscar o usuário algumas vezes para garantir que o commit seja visível
                Usuario usuario = null;
                int maxTentativas = 3;
                for (int tentativa = 0; tentativa < maxTentativas && usuario == null; tentativa++) {
                    usuario = userRepository.buscarUsuarioPorEmail(emailNormalizado);
                    
                    if (usuario == null) {
                        usuario = userRepository.buscarUsuarioSemAtivo(userId);
                    }
                    
                    // Se ainda não encontrou, espera um pouco antes de tentar novamente
                    if (usuario == null && tentativa < maxTentativas - 1) {
                        try {
                            Thread.sleep(100); // 100ms de espera
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                
                if (usuario == null) {
                    LOGGER.warning(String.format("Usuário cadastrado (ID: %d) mas não encontrado após %d tentativas. Email: %s", userId, maxTentativas, emailNormalizado));
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("message", "Usuário cadastrado com sucesso. Faça login para continuar.");
                    ResponseUtil.sendJsonResponse(exchange, 201, response);
                    return;
                }
                
                LOGGER.info("Novo usuário cadastrado: " + emailNormalizado);
                
                // Gera token JWT para login automático após cadastro
                String token = JwtUtil.generateToken(usuario);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Usuário cadastrado com sucesso");
                response.put("user", Map.of(
                    "id", usuario.getIdUsuario(),
                    "name", usuario.getNome(),
                    "email", usuario.getEmail(),
                    "token", token
                ));
                
                ResponseUtil.sendJsonResponse(exchange, 201, response);
                
            } catch (RuntimeException e) {
                if (e.getMessage().contains("Email já cadastrado")) {
                    ResponseUtil.sendErrorResponse(exchange, 409, "Este email já está em uso.");
                } else {
                    throw e;
                }
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao registrar usuário", e);
            ResponseUtil.sendErrorResponse(exchange, 500, "Erro ao processar registro: " + e.getMessage());
        }
    }
}

