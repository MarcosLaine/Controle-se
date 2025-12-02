package server.validation;

import java.util.regex.Pattern;

/**
 * Utilitário para sanitização de HTML e prevenção de XSS
 */
public class HtmlSanitizer {
    
    // Padrões perigosos de XSS
    private static final Pattern[] XSS_PATTERNS = {
        // Script tags
        Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        // Event handlers
        Pattern.compile("on\\w+\\s*=", Pattern.CASE_INSENSITIVE),
        // JavaScript protocol
        Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
        // VBScript
        Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE),
        // Data URIs com scripts
        Pattern.compile("data:text/html", Pattern.CASE_INSENSITIVE),
        // Iframes
        Pattern.compile("<iframe[^>]*>.*?</iframe>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        // Object/embed tags
        Pattern.compile("<(object|embed)[^>]*>.*?</(object|embed)>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        // Style tags com expressões
        Pattern.compile("<style[^>]*>.*?</style>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        // Links com javascript
        Pattern.compile("<a[^>]*href\\s*=\\s*[\"']?javascript:", Pattern.CASE_INSENSITIVE),
        // Img tags com onerror
        Pattern.compile("<img[^>]*onerror", Pattern.CASE_INSENSITIVE),
    };
    
    // Caracteres HTML perigosos que devem ser escapados
    private static final String[] HTML_ESCAPES = {
        "<", "&lt;",
        ">", "&gt;",
        "\"", "&quot;",
        "'", "&#x27;",
        "/", "&#x2F;"
    };
    
    /**
     * Sanitiza uma string removendo ou escapando conteúdo HTML perigoso
     * 
     * @param input String a ser sanitizada
     * @return String sanitizada
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        
        String sanitized = input;
        
        // Remove padrões XSS perigosos
        for (Pattern pattern : XSS_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll("");
        }
        
        // Escapa caracteres HTML básicos
        sanitized = escapeHtml(sanitized);
        
        return sanitized;
    }
    
    /**
     * Escapa caracteres HTML básicos
     */
    private static String escapeHtml(String input) {
        if (input == null) {
            return null;
        }
        
        String escaped = input;
        for (int i = 0; i < HTML_ESCAPES.length; i += 2) {
            escaped = escaped.replace(HTML_ESCAPES[i], HTML_ESCAPES[i + 1]);
        }
        return escaped;
    }
    
    /**
     * Verifica se uma string contém conteúdo HTML perigoso
     * 
     * @param input String a ser verificada
     * @return true se contém conteúdo perigoso
     */
    public static boolean containsDangerousContent(String input) {
        if (input == null) {
            return false;
        }
        
        for (Pattern pattern : XSS_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Remove todas as tags HTML, mantendo apenas o texto
     * 
     * @param input String com HTML
     * @return String apenas com texto
     */
    public static String stripHtml(String input) {
        if (input == null) {
            return null;
        }
        
        // Remove todas as tags HTML
        String stripped = input.replaceAll("<[^>]+>", "");
        // Decodifica entidades HTML básicas
        stripped = stripped.replace("&lt;", "<")
                          .replace("&gt;", ">")
                          .replace("&quot;", "\"")
                          .replace("&#x27;", "'")
                          .replace("&#x2F;", "/")
                          .replace("&amp;", "&");
        
        return stripped.trim();
    }
    
    /**
     * Sanitiza uma string para uso em descrições (permite formatação básica)
     * Remove apenas conteúdo perigoso, mas mantém quebras de linha
     */
    public static String sanitizeForDescription(String input) {
        if (input == null) {
            return null;
        }
        
        String sanitized = input;
        
        // Remove apenas padrões perigosos, não todas as tags
        for (Pattern pattern : XSS_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll("");
        }
        
        // Remove caracteres de controle perigosos, mas mantém \n e \r
        sanitized = sanitized.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        
        // Normaliza quebras de linha
        sanitized = sanitized.replaceAll("\\r\\n", "\n")
                             .replaceAll("\\r", "\n");
        
        return sanitized;
    }
}

