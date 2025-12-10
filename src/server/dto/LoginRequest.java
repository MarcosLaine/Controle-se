package server.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO para requisição de login
 */
public class LoginRequest {
    
    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email inválido")
    @Size(max = 255, message = "Email não pode exceder 255 caracteres")
    private String email;
    
    @NotBlank(message = "Senha é obrigatória")
    @Size(max = 200, message = "Senha não pode exceder 200 caracteres")
    private String password;
    
    private String captchaToken;
    
    public LoginRequest() {}
    
    public LoginRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getCaptchaToken() {
        return captchaToken;
    }
    
    public void setCaptchaToken(String captchaToken) {
        this.captchaToken = captchaToken;
    }
}






