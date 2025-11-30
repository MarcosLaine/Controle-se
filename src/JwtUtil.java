import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Minimal JWT utility using HS256 for signing. Tokens are generated with a
 * configurable secret and validated ensuring expiration and signature checks.
 */
public final class JwtUtil {
    private static final long EXPIRATION_SECONDS = 60L * 60L * 24L; // 24 hours
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SECRET = initializeSecret();
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private JwtUtil() {}

    private static String initializeSecret() {
        String envSecret = System.getenv("JWT_SECRET");
        if (envSecret != null && !envSecret.isBlank()) {
            return envSecret.trim();
        }

        File dataDir = new File("data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        File secretFile = new File(dataDir, "jwt_secret.key");
        if (secretFile.exists()) {
            try {
                return Files.readString(secretFile.toPath(), StandardCharsets.UTF_8).trim();
            } catch (IOException ignored) {}
        }

        byte[] secretBytes = new byte[48];
        RANDOM.nextBytes(secretBytes);
        String generatedSecret = Base64.getEncoder().encodeToString(secretBytes);
        try {
            Files.writeString(secretFile.toPath(), generatedSecret, StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
        return generatedSecret;
    }

    public static String generateToken(Usuario usuario) {
        long issuedAt = Instant.now().getEpochSecond();
        long expiresAt = issuedAt + EXPIRATION_SECONDS;

        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payloadJson = new StringBuilder("{")
            .append("\"sub\":").append(usuario.getIdUsuario()).append(',')
            .append("\"email\":\"").append(escape(usuario.getEmail())).append("\",")
            .append("\"name\":\"").append(escape(usuario.getNome())).append("\",")
            .append("\"iat\":").append(issuedAt).append(',')
            .append("\"exp\":").append(expiresAt)
            .append('}')
            .toString();

        String header = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));
        String payload = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signature;
        try {
            signature = base64UrlEncode(sign(header + "." + payload));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao assinar token JWT", e);
        }
        return header + "." + payload + "." + signature;
    }

    public static JwtValidationResult validateToken(String token) {
        if (token == null || token.isBlank()) {
            return JwtValidationResult.invalid("Token ausente");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return JwtValidationResult.invalid("Token malformado");
        }

        try {
            byte[] expectedSignature = sign(parts[0] + "." + parts[1]);
            byte[] providedSignature = BASE64_URL_DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(expectedSignature, providedSignature)) {
                return JwtValidationResult.invalid("Assinatura inválida");
            }

            String payloadJson = new String(BASE64_URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
            long exp = extractLong(payloadJson, "exp");
            if (Instant.now().getEpochSecond() > exp) {
                return JwtValidationResult.invalid("Token expirado");
            }

            long userId = extractLong(payloadJson, "sub");
            String email = extractString(payloadJson, "email");
            String name = extractString(payloadJson, "name");
            return JwtValidationResult.valid((int) userId, email, name);
        } catch (Exception e) {
            return JwtValidationResult.invalid("Falha ao validar token");
        }
    }

    private static byte[] sign(String data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String base64UrlEncode(byte[] input) {
        return BASE64_URL_ENCODER.encodeToString(input);
    }

    private static String extractString(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = p.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static long extractLong(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)");
        Matcher matcher = p.matcher(json);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }
        return 0L;
    }

    public static final class JwtValidationResult {
        private final boolean valid;
        private final String message;
        private final int userId;
        private final String email;
        private final String name;

        private JwtValidationResult(boolean valid, String message, int userId, String email, String name) {
            this.valid = valid;
            this.message = message;
            this.userId = userId;
            this.email = email;
            this.name = name;
        }

        public static JwtValidationResult valid(int userId, String email, String name) {
            return new JwtValidationResult(true, null, userId, email, name);
        }

        public static JwtValidationResult invalid(String message) {
            return new JwtValidationResult(false, message, -1, null, null);
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }

        public int getUserId() {
            return userId;
        }

        public String getEmail() {
            return email;
        }

        public String getName() {
            return name;
        }
    }
}

