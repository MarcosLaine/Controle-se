package server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import server.model.Usuario;
import server.repository.UserRepository;
import server.security.CaptchaValidator;
import server.security.JwtUtil;
import server.security.LoginAttemptTracker;
import server.utils.JsonUtil;
import server.utils.RequestUtil;
import server.utils.ResponseUtil;
import server.validation.InputValidator;
import server.validation.ValidationResult;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoginHandler implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(LoginHandler.class.getName());
    
    private final UserRepository userRepository;
    private final LoginAttemptTracker loginAttemptTracker;
    private final CaptchaValidator captchaValidator;

    public LoginHandler(UserRepository userRepository, LoginAttemptTracker loginAttemptTracker, CaptchaValidator captchaValidator) {
        this.userRepository = userRepository;
        this.loginAttemptTracker = loginAttemptTracker;
        this.captchaValidator = captchaValidator;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            ResponseUtil.sendErrorResponse(exchange, 405, "Método não permitido");
            return;
        }
        
        String clientIp = getClientIp(exchange);
        
        try {
            String requestBody = RequestUtil.readRequestBody(exchange);
            LOGGER.info("Request body recebido: " + requestBody.substring(0, Math.min(200, requestBody.length())) + (requestBody.length() > 200 ? "..." : ""));
            
            Map<String, String> data = JsonUtil.parseJson(requestBody);
            
            String email = data.get("email");
            String password = data.get("password");
            String captchaToken = data.get("captchaToken");
            
            ValidationResult validation = new ValidationResult();
            validation.addErrors(InputValidator.validateEmail(email, true).getErrors());
            if (password == null || password.isEmpty()) {
                validation.addError("Senha é obrigatória");
            }
            
            if (!validation.isValid()) {
                ResponseUtil.sendErrorResponse(exchange, 400, validation.getErrorMessage());
                return;
            }
            
            email = email.toLowerCase().trim();
            
            if (captchaToken != null) {
                LOGGER.info(String.format("CAPTCHA token recebido (tamanho: %d caracteres)", captchaToken.length()));
            } else {
                LOGGER.warning("CAPTCHA token não recebido no request. Chaves disponíveis: " + data.keySet());
            }
            
            boolean requiresCaptcha = loginAttemptTracker.shouldRequireCaptcha(clientIp, email);
            if (requiresCaptcha) {
                if (captchaToken == null || captchaToken.isEmpty()) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "CAPTCHA requerido após múltiplas tentativas falhas");
                    response.put("requiresCaptcha", true);
                    response.put("failedAttempts", loginAttemptTracker.getAttemptInfo(clientIp, email).failedAttempts);
                    
                    ResponseUtil.sendJsonResponse(exchange, 401, response);
                    return;
                }
                
                LOGGER.info(String.format("Validando CAPTCHA para email: %s, IP: %s", email, clientIp));
                boolean captchaValid = captchaValidator.validate(captchaToken, clientIp);
                LOGGER.info(String.format("Resultado da validação CAPTCHA: %s", captchaValid ? "VÁLIDO" : "INVÁLIDO"));
                
                if (!captchaValid) {
                    loginAttemptTracker.recordFailedAttempt(clientIp, email);
                    
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "CAPTCHA inválido. Tente novamente.");
                    response.put("requiresCaptcha", true);
                    response.put("failedAttempts", loginAttemptTracker.getAttemptInfo(clientIp, email).failedAttempts);
                    
                    ResponseUtil.sendJsonResponse(exchange, 401, response);
                    return;
                }
                LOGGER.info("CAPTCHA validado com sucesso, prosseguindo com autenticação");
            }
            
            if (loginAttemptTracker.isBlocked(clientIp, email)) {
                LoginAttemptTracker.AttemptInfo info = loginAttemptTracker.getAttemptInfo(clientIp, email);
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Muitas tentativas falhas. Acesso bloqueado temporariamente.");
                response.put("blocked", true);
                response.put("unlockTime", info.unlockTime);
                response.put("failedAttempts", info.failedAttempts);
                
                ResponseUtil.sendJsonResponse(exchange, 429, response);
                return;
            }
            
            if (userRepository.autenticarUsuario(email, password)) {
                loginAttemptTracker.recordSuccessfulAttempt(clientIp, email);
                
                Usuario usuario = userRepository.buscarUsuarioPorEmail(email);
                String token = JwtUtil.generateToken(usuario);
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("user", Map.of(
                    "id", usuario.getIdUsuario(),
                    "name", usuario.getNome(),
                    "email", usuario.getEmail(),
                    "token", token
                ));
                
                ResponseUtil.sendJsonResponse(exchange, 200, response);
            } else {
                loginAttemptTracker.recordFailedAttempt(clientIp, email);
                
                LoginAttemptTracker.AttemptInfo info = loginAttemptTracker.getAttemptInfo(clientIp, email);
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Email ou senha incorretos");
                response.put("failedAttempts", info.failedAttempts);
                response.put("requiresCaptcha", info.failedAttempts >= 3);
                
                ResponseUtil.sendJsonResponse(exchange, 401, response);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao processar login", e);
            ResponseUtil.sendErrorResponse(exchange, 500, "Erro interno do servidor");
        }
    }
    
    private String getClientIp(HttpExchange exchange) {
        // Tenta obter do header X-Forwarded-For (útil para proxies/load balancers)
        String forwardedFor = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            String[] ips = forwardedFor.split(",");
            if (ips.length > 0) {
                String ip = ips[0].trim();
                if (!ip.isEmpty() && !ip.equals("unknown")) {
                    // Normaliza localhost para IPv4
                    if (ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1")) {
                        return "127.0.0.1";
                    }
                    return ip;
                }
            }
        }
        
        // Tenta obter do header X-Real-IP
        String realIp = exchange.getRequestHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            String ip = realIp.trim();
            if (!ip.isEmpty() && !ip.equals("unknown")) {
                // Normaliza localhost para IPv4
                if (ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1")) {
                    return "127.0.0.1";
                }
                return ip;
            }
        }
        
        // Fallback para o IP remoto da conexão
        InetSocketAddress remoteAddress = exchange.getRemoteAddress();
        if (remoteAddress != null) {
            String ip = remoteAddress.getAddress().getHostAddress();
            // Normaliza localhost para IPv4
            if (ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1")) {
                return "127.0.0.1";
            }
            return ip;
        }
        
        // Último recurso: retorna localhost em vez de "unknown"
        return "127.0.0.1";
    }
}

