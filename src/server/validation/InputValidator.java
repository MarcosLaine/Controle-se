package server.validation;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * Validador centralizado para entrada de dados
 * Implementa validações robustas conforme JSR-303 (Bean Validation)
 */
public class InputValidator {
    
    // Padrões de validação
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern BRAZILIAN_DATE_PATTERN = Pattern.compile(
        "^\\d{4}-\\d{2}-\\d{2}$" // ISO 8601: YYYY-MM-DD
    );
    
    private static final Pattern BRAZILIAN_MONEY_PATTERN = Pattern.compile(
        "^[0-9]+(\\.[0-9]{1,2})?$" // Permite números com até 2 casas decimais
    );
    
    // Limites padrão
    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_PASSWORD_LENGTH = 200;
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_TAG_NAME_LENGTH = 50;
    private static final int MAX_TAG_COLOR_LENGTH = 20;
    
    // Valores monetários
    private static final double MAX_MONEY_VALUE = 999999999.99;
    private static final double MIN_MONEY_VALUE = -999999999.99;
    
    /**
     * Valida uma string genérica
     */
    public static ValidationResult validateString(String fieldName, String value, int maxLength, boolean required) {
        ValidationResult result = new ValidationResult();
        
        if (value == null || value.trim().isEmpty()) {
            if (required) {
                result.addError(fieldName + " é obrigatório");
            }
            return result;
        }
        
        String trimmed = value.trim();
        
        // Valida tamanho
        if (trimmed.length() > maxLength) {
            result.addError(fieldName + " excede o tamanho máximo de " + maxLength + " caracteres");
        }
        
        // Verifica caracteres de controle perigosos
        if (containsControlCharacters(trimmed)) {
            result.addError(fieldName + " contém caracteres inválidos");
        }
        
        return result;
    }
    
    /**
     * Valida nome (sem HTML, sem caracteres especiais perigosos)
     */
    public static ValidationResult validateName(String fieldName, String value, boolean required) {
        ValidationResult result = validateString(fieldName, value, MAX_NAME_LENGTH, required);
        
        if (!result.isValid() && value != null) {
            return result;
        }
        
        if (value != null && !value.trim().isEmpty()) {
            // Verifica padrões SQL injection suspeitos
            if (containsSqlInjectionPatterns(value)) {
                result.addError(fieldName + " contém caracteres não permitidos");
            }
            
            // Verifica se contém HTML perigoso
            if (HtmlSanitizer.containsDangerousContent(value)) {
                result.addError(fieldName + " contém conteúdo HTML não permitido");
            }
        }
        
        return result;
    }
    
    /**
     * Valida email
     */
    public static ValidationResult validateEmail(String email, boolean required) {
        ValidationResult result = new ValidationResult();
        
        if (email == null || email.trim().isEmpty()) {
            if (required) {
                result.addError("Email é obrigatório");
            }
            return result;
        }
        
        String trimmed = email.trim().toLowerCase();
        
        // Valida tamanho
        if (trimmed.length() > MAX_EMAIL_LENGTH) {
            result.addError("Email excede o tamanho máximo de " + MAX_EMAIL_LENGTH + " caracteres");
        }
        
        // Valida formato
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            result.addError("Email inválido");
        }
        
        // Verifica caracteres perigosos
        if (containsControlCharacters(trimmed)) {
            result.addError("Email contém caracteres inválidos");
        }
        
        return result;
    }
    
    /**
     * Valida senha
     */
    public static ValidationResult validatePassword(String password, boolean required) {
        ValidationResult result = new ValidationResult();
        
        if (password == null || password.isEmpty()) {
            if (required) {
                result.addError("Senha é obrigatória");
            }
            return result;
        }
        
        // Valida tamanho mínimo
        if (password.length() < MIN_PASSWORD_LENGTH) {
            result.addError("Senha deve ter no mínimo " + MIN_PASSWORD_LENGTH + " caracteres");
        }
        
        // Valida tamanho máximo
        if (password.length() > MAX_PASSWORD_LENGTH) {
            result.addError("Senha excede o tamanho máximo de " + MAX_PASSWORD_LENGTH + " caracteres");
        }
        
        return result;
    }
    
    /**
     * Valida descrição (permite mais caracteres, sanitiza HTML)
     */
    public static ValidationResult validateDescription(String fieldName, String value, boolean required) {
        ValidationResult result = validateString(fieldName, value, MAX_DESCRIPTION_LENGTH, required);
        
        if (!result.isValid() && value != null) {
            return result;
        }
        
        if (value != null && !value.trim().isEmpty()) {
            // Verifica se contém HTML perigoso
            if (HtmlSanitizer.containsDangerousContent(value)) {
                result.addError(fieldName + " contém conteúdo HTML perigoso");
            }
        }
        
        return result;
    }
    
    /**
     * Valida data no formato ISO 8601 (YYYY-MM-DD)
     */
    public static ValidationResult validateDate(String fieldName, String dateStr, boolean required) {
        ValidationResult result = new ValidationResult();
        
        if (dateStr == null || dateStr.trim().isEmpty()) {
            if (required) {
                result.addError(fieldName + " é obrigatório");
            }
            return result;
        }
        
        String trimmed = dateStr.trim();
        
        // Valida formato básico
        if (!BRAZILIAN_DATE_PATTERN.matcher(trimmed).matches()) {
            result.addError(fieldName + " deve estar no formato YYYY-MM-DD");
            return result;
        }
        
        // Tenta fazer parse da data
        try {
            LocalDate date = LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
            
            // Valida se a data não é muito antiga ou futura
            LocalDate minDate = LocalDate.of(1900, 1, 1);
            LocalDate maxDate = LocalDate.of(2100, 12, 31);
            
            if (date.isBefore(minDate) || date.isAfter(maxDate)) {
                result.addError(fieldName + " deve estar entre 1900-01-01 e 2100-12-31");
            }
        } catch (DateTimeParseException e) {
            result.addError(fieldName + " é uma data inválida");
        }
        
        return result;
    }
    
    /**
     * Valida valor monetário
     */
    public static ValidationResult validateMoney(String fieldName, Double value, boolean required) {
        ValidationResult result = new ValidationResult();
        
        if (value == null) {
            if (required) {
                result.addError(fieldName + " é obrigatório");
            }
            return result;
        }
        
        // Verifica se é NaN ou infinito
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            result.addError(fieldName + " deve ser um número válido");
            return result;
        }
        
        // Valida faixa de valores
        if (value < MIN_MONEY_VALUE || value > MAX_MONEY_VALUE) {
            result.addError(fieldName + " deve estar entre " + MIN_MONEY_VALUE + " e " + MAX_MONEY_VALUE);
        }
        
        // Valida casas decimais (máximo 2)
        double rounded = Math.round(value * 100.0) / 100.0;
        if (Math.abs(value - rounded) > 0.001) {
            result.addError(fieldName + " não pode ter mais de 2 casas decimais");
        }
        
        return result;
    }
    
    /**
     * Valida valor monetário a partir de string
     */
    public static ValidationResult validateMoneyString(String fieldName, String valueStr, boolean required) {
        ValidationResult result = new ValidationResult();
        
        if (valueStr == null || valueStr.trim().isEmpty()) {
            if (required) {
                result.addError(fieldName + " é obrigatório");
            }
            return result;
        }
        
        try {
            // Remove espaços e caracteres de formatação brasileira
            String cleaned = valueStr.trim()
                .replace(".", "")  // Remove separador de milhar
                .replace(",", "."); // Converte vírgula para ponto decimal
            
            double value = Double.parseDouble(cleaned);
            return validateMoney(fieldName, value, required);
        } catch (NumberFormatException e) {
            result.addError(fieldName + " deve ser um número válido");
            return result;
        }
    }
    
    /**
     * Valida ID (deve ser positivo)
     */
    public static ValidationResult validateId(String fieldName, Integer id, boolean required) {
        ValidationResult result = new ValidationResult();
        
        if (id == null) {
            if (required) {
                result.addError(fieldName + " é obrigatório");
            }
            return result;
        }
        
        if (id <= 0) {
            result.addError(fieldName + " deve ser um número positivo");
        }
        
        return result;
    }
    
    /**
     * Valida enum/valor permitido
     */
    public static ValidationResult validateEnum(String fieldName, String value, String[] allowedValues, boolean required) {
        ValidationResult result = new ValidationResult();
        
        if (value == null || value.trim().isEmpty()) {
            if (required) {
                result.addError(fieldName + " é obrigatório");
            }
            return result;
        }
        
        boolean found = false;
        for (String allowed : allowedValues) {
            if (allowed.equalsIgnoreCase(value.trim())) {
                found = true;
                break;
            }
        }
        
        if (!found) {
            result.addError(fieldName + " deve ser um dos valores: " + String.join(", ", allowedValues));
        }
        
        return result;
    }
    
    /**
     * Valida dia do mês (1-31)
     */
    public static ValidationResult validateDayOfMonth(String fieldName, Integer day, boolean required) {
        ValidationResult result = new ValidationResult();
        
        if (day == null) {
            if (required) {
                result.addError(fieldName + " é obrigatório");
            }
            return result;
        }
        
        if (day < 1 || day > 31) {
            result.addError(fieldName + " deve estar entre 1 e 31");
        }
        
        return result;
    }
    
    /**
     * Valida cor (formato hex ou nome)
     */
    public static ValidationResult validateColor(String fieldName, String color, boolean required) {
        ValidationResult result = validateString(fieldName, color, MAX_TAG_COLOR_LENGTH, required);
        
        if (!result.isValid() && color != null) {
            return result;
        }
        
        if (color != null && !color.trim().isEmpty()) {
            String trimmed = color.trim();
            // Valida formato hex (#RRGGBB ou #RGB)
            if (!trimmed.matches("^#[0-9A-Fa-f]{3}([0-9A-Fa-f]{3})?$")) {
                result.addError(fieldName + " deve estar no formato hexadecimal (#RRGGBB)");
            }
        }
        
        return result;
    }
    
    // Métodos auxiliares privados
    
    private static boolean containsControlCharacters(String input) {
        if (input == null) return false;
        // Permite \n, \r, \t, mas bloqueia outros caracteres de controle
        return input.matches(".*[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F].*");
    }
    
    private static boolean containsSqlInjectionPatterns(String input) {
        if (input == null) return false;
        String lower = input.toLowerCase();
        String[] dangerousPatterns = {
            "';", "--", "/*", "*/", "xp_", "sp_", 
            "exec", "execute", "union", "select", 
            "insert", "update", "delete", "drop", 
            "create", "alter", "truncate"
        };
        for (String pattern : dangerousPatterns) {
            if (lower.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Sanitiza uma string para uso seguro
     */
    public static String sanitizeInput(String input) {
        if (input == null) return null;
        // Remove caracteres de controle
        String sanitized = input.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        // Normaliza espaços
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        return sanitized;
    }
    
    /**
     * Sanitiza descrição (remove HTML perigoso, mantém formatação básica)
     */
    public static String sanitizeDescription(String input) {
        if (input == null) return null;
        return HtmlSanitizer.sanitizeForDescription(input);
    }
}

