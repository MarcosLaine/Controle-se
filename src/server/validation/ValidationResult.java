package server.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultado de uma validação, contendo erros encontrados
 */
public class ValidationResult {
    private List<String> errors;
    private boolean valid;
    
    public ValidationResult() {
        this.errors = new ArrayList<>();
        this.valid = true;
    }
    
    /**
     * Adiciona um erro à lista de erros
     */
    public void addError(String error) {
        this.errors.add(error);
        this.valid = false;
    }
    
    /**
     * Adiciona múltiplos erros
     */
    public void addErrors(List<String> errors) {
        this.errors.addAll(errors);
        if (!errors.isEmpty()) {
            this.valid = false;
        }
    }
    
    /**
     * Verifica se a validação passou
     */
    public boolean isValid() {
        return valid;
    }
    
    /**
     * Retorna a lista de erros
     */
    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }
    
    /**
     * Retorna a mensagem de erro combinada
     */
    public String getErrorMessage() {
        if (errors.isEmpty()) {
            return "";
        }
        return String.join("; ", errors);
    }
    
    /**
     * Lança uma exceção se a validação falhou
     */
    public void throwIfInvalid() {
        if (!valid) {
            throw new IllegalArgumentException(getErrorMessage());
        }
    }
    
    /**
     * Cria um resultado válido
     */
    public static ValidationResult valid() {
        return new ValidationResult();
    }
    
    /**
     * Cria um resultado inválido com um erro
     */
    public static ValidationResult invalid(String error) {
        ValidationResult result = new ValidationResult();
        result.addError(error);
        return result;
    }
}

