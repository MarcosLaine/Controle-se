package server.services;

import server.utils.JsonUtil;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class BrevoEmailService {
    private static final Logger LOGGER = Logger.getLogger(BrevoEmailService.class.getName());
    private static final String BREVO_SEND_EMAIL_URL = "https://api.brevo.com/v3/smtp/email";
    private static final int TIMEOUT_MS = 10000;

    private final String apiKey;
    private final String senderEmail;
    private final String senderName;
    private final String replyToEmail;
    private final String replyToName;

    public BrevoEmailService() {
        this.apiKey = readEnv("BREVO_API_KEY");
        this.senderEmail = readEnv("BREVO_SENDER_EMAIL");
        this.senderName = readEnvOrDefault("BREVO_SENDER_NAME", "Controle-se");
        this.replyToEmail = readEnv("BREVO_REPLY_TO_EMAIL");
        this.replyToName = readEnvOrDefault("BREVO_REPLY_TO_NAME", this.senderName);
    }

    public boolean isConfigured() {
        return !apiKey.isBlank() && !senderEmail.isBlank();
    }

    public boolean sendPasswordResetEmail(String recipientEmail, String recipientName, String resetLink) {
        if (!isConfigured()) {
            LOGGER.warning("Brevo nao configurado. Defina BREVO_API_KEY e BREVO_SENDER_EMAIL para habilitar o envio real.");
            return false;
        }

        String safeName = (recipientName == null || recipientName.isBlank()) ? "usuario" : recipientName.trim();

        Map<String, Object> sender = new LinkedHashMap<>();
        sender.put("name", senderName);
        sender.put("email", senderEmail);

        Map<String, Object> recipient = new LinkedHashMap<>();
        recipient.put("email", recipientEmail);
        if (recipientName != null && !recipientName.isBlank()) {
            recipient.put("name", recipientName.trim());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sender", sender);

        List<Map<String, Object>> recipients = new ArrayList<>();
        recipients.add(recipient);
        payload.put("to", recipients);
        payload.put("subject", "Redefinicao de senha - Controle-se");
        payload.put("htmlContent", buildHtmlContent(safeName, resetLink));
        payload.put("textContent", buildTextContent(safeName, resetLink));
        payload.put("tags", List.of("password-reset"));

        if (!replyToEmail.isBlank()) {
            Map<String, Object> replyTo = new LinkedHashMap<>();
            replyTo.put("email", replyToEmail);
            if (!replyToName.isBlank()) {
                replyTo.put("name", replyToName);
            }
            payload.put("replyTo", replyTo);
        }

        return send(payload, recipientEmail);
    }

    private boolean send(Map<String, Object> payload, String recipientEmail) {
        HttpURLConnection connection = null;

        try {
            URL url = URI.create(BREVO_SEND_EMAIL_URL).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("accept", "application/json");
            connection.setRequestProperty("content-type", "application/json");
            connection.setRequestProperty("api-key", apiKey);

            String requestBody = JsonUtil.toJson(payload);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            String responseBody = readResponseBody(connection, responseCode);

            if (responseCode >= 200 && responseCode < 300) {
                LOGGER.info("Email transacional enviado via Brevo para " + recipientEmail + ": " + responseBody);
                return true;
            }

            LOGGER.warning("Falha ao enviar email via Brevo para " + recipientEmail + " - HTTP " + responseCode + ": " + responseBody);
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao enviar email via Brevo", e);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readResponseBody(HttpURLConnection connection, int responseCode) {
        InputStream stream = null;
        try {
            stream = responseCode >= 200 && responseCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();

            if (stream == null) {
                return "";
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            return "Erro ao ler resposta: " + e.getMessage();
        }
    }

    private static String buildHtmlContent(String recipientName, String resetLink) {
        String escapedName = escapeHtml(recipientName);
        String escapedLink = escapeHtml(resetLink);
        return "<html><body style=\"font-family:Arial,sans-serif;color:#111827;line-height:1.6;\">"
            + "<h2>Redefinicao de senha</h2>"
            + "<p>Ola, " + escapedName + ".</p>"
            + "<p>Recebemos uma solicitacao para redefinir a sua senha no Controle-se.</p>"
            + "<p>Clique no botao abaixo para criar uma nova senha:</p>"
            + "<p><a href=\"" + escapedLink + "\" style=\"display:inline-block;padding:12px 20px;background:#2563eb;color:#ffffff;text-decoration:none;border-radius:8px;\">Redefinir senha</a></p>"
            + "<p>Se o botao nao funcionar, copie e cole este link no navegador:</p>"
            + "<p><a href=\"" + escapedLink + "\">" + escapedLink + "</a></p>"
            + "<p>Este link expira em 1 hora.</p>"
            + "<p>Se voce nao solicitou esta alteracao, ignore este email.</p>"
            + "</body></html>";
    }

    private static String buildTextContent(String recipientName, String resetLink) {
        return "Ola, " + recipientName + ".\n\n"
            + "Recebemos uma solicitacao para redefinir a sua senha no Controle-se.\n\n"
            + "Use o link abaixo para criar uma nova senha:\n"
            + resetLink + "\n\n"
            + "Este link expira em 1 hora.\n"
            + "Se voce nao solicitou esta alteracao, ignore este email.";
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private static String readEnv(String key) {
        String value = System.getenv(key);
        return value == null ? "" : value.trim();
    }

    private static String readEnvOrDefault(String key, String fallback) {
        String value = readEnv(key);
        return value.isBlank() ? fallback : value;
    }
}
