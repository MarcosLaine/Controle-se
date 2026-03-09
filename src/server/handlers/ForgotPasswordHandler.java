package server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import server.model.Usuario;
import server.repository.UserRepository;
import server.services.BrevoEmailService;
import server.utils.JsonUtil;
import server.utils.RequestUtil;
import server.utils.ResponseUtil;
import server.validation.InputValidator;
import server.validation.ValidationResult;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class ForgotPasswordHandler implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(ForgotPasswordHandler.class.getName());
    private static final int TOKEN_VALIDITY_MINUTES = 60;

    private final UserRepository userRepository;
    private final BrevoEmailService brevoEmailService;

    public ForgotPasswordHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.brevoEmailService = new BrevoEmailService();
    }

    public ForgotPasswordHandler(UserRepository userRepository, BrevoEmailService brevoEmailService) {
        this.userRepository = userRepository;
        this.brevoEmailService = brevoEmailService;
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
            String email = data != null ? data.get("email") : null;

            ValidationResult emailRes = InputValidator.validateEmail(email, true);
            if (!emailRes.isValid()) {
                ResponseUtil.sendErrorResponse(exchange, 400, emailRes.getErrors().get(0));
                return;
            }

            String emailNormalizado = email.trim().toLowerCase();
            Usuario usuario = userRepository.buscarUsuarioPorEmail(emailNormalizado);

            // Sempre retorna sucesso para não revelar se o email existe
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Se este email estiver cadastrado, você receberá um link para redefinir sua senha.");

            if (usuario != null) {
                String token = UserRepository.gerarTokenRecuperacaoSenha();
                Instant expiresAt = Instant.now().plusSeconds(TOKEN_VALIDITY_MINUTES * 60L);
                userRepository.criarTokenRecuperacaoSenha(usuario.getIdUsuario(), token, expiresAt);

                String baseUrl = baseUrlForResetLink(exchange);
                String resetLink = baseUrl + "/redefinir-senha?token=" + token;

                boolean emailSent = brevoEmailService.sendPasswordResetEmail(
                    usuario.getEmail(),
                    usuario.getNome(),
                    resetLink
                );

                if (emailSent) {
                    LOGGER.info("Email de recuperação enviado para: " + emailNormalizado);
                } else {
                    LOGGER.warning("Nao foi possivel enviar email via Brevo para: " + emailNormalizado);
                }

                LOGGER.info("Recuperação de senha solicitada para: " + emailNormalizado + " | Link (use em dev): " + resetLink);

                // Em desenvolvimento, pode retornar o link na resposta como fallback.
                if ("true".equalsIgnoreCase(System.getenv("FORGOT_PASSWORD_RETURN_LINK"))) {
                    response.put("resetLink", resetLink);
                }
            }

            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            LOGGER.warning("Erro em forgot-password: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Se este email estiver cadastrado, você receberá um link para redefinir sua senha.");
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        }
    }

    private static String baseUrlForResetLink(HttpExchange exchange) {
        String env = System.getenv("FORGOT_PASSWORD_BASE_URL");
        if (env != null && !env.isBlank()) {
            return env.replaceAll("/$", "");
        }
        String host = exchange.getRequestHeaders().getFirst("Host");
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && !origin.isBlank()) {
            return origin.replaceAll("/$", "");
        }
        if (host != null && !host.isBlank()) {
            return "https://" + host;
        }
        return "http://localhost:5173";
    }
}
