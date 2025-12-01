package server.security;

import java.io.*;
import java.net.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Validador de CAPTCHA usando Google reCAPTCHA v2
 * Valida tokens do reCAPTCHA enviados pelo frontend
 * 
 * CONFIGURAÇÃO:
 * 1. Obtenha as chaves em: https://www.google.com/recaptcha/admin/create
 * 2. Configure a variável de ambiente: RECAPTCHA_SECRET_KEY=sua_secret_key
 * 3. No frontend, configure: VITE_RECAPTCHA_SITE_KEY=sua_site_key
 * 
 * Para desenvolvimento/teste, use as chaves de teste do Google:
 * - Site Key: 6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI
 * - Secret Key: 6LeIxAcTAAAAAGG-vFI1TnRWxMZNFuojJ4WifJWe
 */
public class CaptchaValidator {
    private static final Logger LOGGER = Logger.getLogger(CaptchaValidator.class.getName());
    
    // URL da API de verificação do Google reCAPTCHA
    private static final String RECAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
    
    // Secret key (deve ser configurada via variável de ambiente)
    private final String secretKey;
    
    // Timeout para requisições HTTP (5 segundos)
    private static final int TIMEOUT_MS = 5000;
    
    public CaptchaValidator() {
        // Lê a secret key de variável de ambiente
        this.secretKey = System.getenv("RECAPTCHA_SECRET_KEY");
        
        if (this.secretKey == null || this.secretKey.isEmpty()) {
            LOGGER.warning("RECAPTCHA_SECRET_KEY não configurada. CAPTCHA será desabilitado.");
        }
    }
    
    public CaptchaValidator(String secretKey) {
        this.secretKey = secretKey;
    }
    
    /**
     * Valida um token do reCAPTCHA
     * @param captchaToken Token enviado pelo frontend
     * @param clientIp IP do cliente (opcional, mas recomendado)
     * @return true se válido, false caso contrário
     */
    public boolean validate(String captchaToken, String clientIp) {
        // Se não há secret key configurada, aceita qualquer token (modo desenvolvimento)
        if (secretKey == null || secretKey.isEmpty()) {
            LOGGER.warning("CAPTCHA desabilitado: RECAPTCHA_SECRET_KEY não configurada - aceitando token");
            return true; // Em desenvolvimento, aceita sem validação
        }
        
        // Se o token está vazio, rejeita
        if (captchaToken == null || captchaToken.isEmpty()) {
            LOGGER.warning("Token CAPTCHA vazio ou nulo");
            return false;
        }
        
        LOGGER.info(String.format("Validando CAPTCHA token (tamanho: %d, IP: %s)", captchaToken.length(), clientIp != null ? clientIp : "unknown"));
        
        try {
            // Constrói a URL com parâmetros usando POST (mais seguro para tokens longos)
            String postData = "secret=" + URLEncoder.encode(secretKey, "UTF-8") +
                "&response=" + URLEncoder.encode(captchaToken, "UTF-8");
            
            // Adiciona IP do cliente se fornecido (recomendado pelo Google)
            if (clientIp != null && !clientIp.isEmpty() && !clientIp.equals("unknown")) {
                postData += "&remoteip=" + URLEncoder.encode(clientIp, "UTF-8");
            }
            
            // Faz requisição HTTP POST (mais seguro que GET para tokens longos)
            URL url = new URL(RECAPTCHA_VERIFY_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Content-Length", String.valueOf(postData.length()));
            
            // Envia dados POST
            try (OutputStream os = connection.getOutputStream()) {
                os.write(postData.getBytes("UTF-8"));
            }
            
            // Lê a resposta
            int responseCode = connection.getResponseCode();
            
            // Lê o corpo da resposta (pode estar no input stream ou error stream)
            String responseBody;
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (InputStream is = connection.getInputStream();
                     BufferedReader reader = new BufferedReader(
                         new InputStreamReader(is, "UTF-8"))) {
                    responseBody = reader.lines().collect(Collectors.joining("\n"));
                }
            } else {
                // Tenta ler do error stream
                try (InputStream is = connection.getErrorStream();
                     BufferedReader reader = new BufferedReader(
                         new InputStreamReader(is, "UTF-8"))) {
                    responseBody = reader.lines().collect(Collectors.joining("\n"));
                } catch (Exception e) {
                    responseBody = "Erro ao ler resposta: " + e.getMessage();
                }
                LOGGER.warning(String.format(
                    "Erro ao validar CAPTCHA: código HTTP %d, resposta: %s", responseCode, responseBody
                ));
                return false;
            }
            
            LOGGER.info(String.format("Resposta do Google reCAPTCHA: %s", responseBody));
            
            // Parse da resposta JSON
            // Formato esperado: {"success": true, "challenge_ts": "...", "hostname": "..."}
            boolean isValid = parseResponse(responseBody);
            
            if (!isValid) {
                LOGGER.warning(String.format(
                    "CAPTCHA inválido. Resposta do Google: %s", responseBody
                ));
            } else {
                LOGGER.info("CAPTCHA validado com sucesso");
            }
            
            return isValid;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao validar CAPTCHA", e);
            // Em caso de erro na validação, por segurança, rejeita
            return false;
        }
    }
    
    /**
     * Faz parse da resposta JSON do Google reCAPTCHA
     * @param jsonResponse Resposta JSON da API
     * @return true se "success": true, false caso contrário
     */
    private boolean parseResponse(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.isEmpty()) {
            LOGGER.warning("Resposta do Google reCAPTCHA está vazia");
            return false;
        }
        
        // Parse simples de JSON (sem biblioteca externa)
        // Remove espaços e quebras de linha para facilitar o parse
        String normalized = jsonResponse.replaceAll("\\s+", " ").trim();
        
        // Procura por "success":true (com diferentes formatos possíveis)
        // Formato esperado: {"success":true,...} ou {"success": true,...}
        if (normalized.matches(".*\"success\"\\s*:\\s*true.*") || 
            normalized.matches(".*'success'\\s*:\\s*true.*")) {
            LOGGER.info("CAPTCHA validado: success=true encontrado na resposta");
            return true;
        }
        
        // Verifica se contém "success":false explicitamente
        if (normalized.matches(".*\"success\"\\s*:\\s*false.*") || 
            normalized.matches(".*'success'\\s*:\\s*false.*")) {
            LOGGER.warning("CAPTCHA inválido: success=false encontrado na resposta");
            return false;
        }
        
        // Se não encontrou padrão conhecido, loga e assume inválido por segurança
        LOGGER.warning(String.format("Não foi possível parsear resposta do CAPTCHA: %s", jsonResponse));
        return false;
    }
    
    /**
     * Verifica se o CAPTCHA está habilitado (secret key configurada)
     * @return true se habilitado, false caso contrário
     */
    public boolean isEnabled() {
        return secretKey != null && !secretKey.isEmpty();
    }
}

