package server.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utilitário para validação usando Bean Validation (JSR-303)
 */
public class BeanValidationUtil {
    
    private static final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
    private static final Validator validator = validatorFactory.getValidator();
    
    /**
     * Valida um objeto usando Bean Validation
     * 
     * @param object Objeto a ser validado
     * @return ValidationResult com os erros encontrados
     */
    public static <T> ValidationResult validate(T object) {
        ValidationResult result = new ValidationResult();
        
        if (object == null) {
            result.addError("Objeto não pode ser nulo");
            return result;
        }
        
        Set<ConstraintViolation<T>> violations = validator.validate(object);
        
        if (!violations.isEmpty()) {
            for (ConstraintViolation<T> violation : violations) {
                String message = violation.getMessage();
                String propertyPath = violation.getPropertyPath().toString();
                
                // Se a mensagem não contém o nome do campo, adiciona
                if (propertyPath != null && !propertyPath.isEmpty() && !message.contains(propertyPath)) {
                    result.addError(propertyPath + ": " + message);
                } else {
                    result.addError(message);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Valida um objeto e retorna mensagens de erro como lista
     */
    public static <T> java.util.List<String> validateAndGetErrors(T object) {
        ValidationResult result = validate(object);
        return result.getErrors();
    }
    
    /**
     * Valida um objeto e retorna true se válido
     */
    public static <T> boolean isValid(T object) {
        return validate(object).isValid();
    }
    
    /**
     * Valida um objeto e lança exceção se inválido
     */
    public static <T> void validateOrThrow(T object) {
        ValidationResult result = validate(object);
        if (!result.isValid()) {
            throw new IllegalArgumentException(result.getErrorMessage());
        }
    }
}



