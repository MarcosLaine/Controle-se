package server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;

/**
 * DTO para requisição de criação/atualização de conta
 */
public class AccountRequest {
    
    private Integer id;
    
    @NotBlank(message = "Nome da conta é obrigatório")
    @Size(max = 100, message = "Nome não pode exceder 100 caracteres")
    private String name;
    
    @NotBlank(message = "Tipo da conta é obrigatório")
    @Pattern(regexp = "^(CORRENTE|POUPANCA|CARTAO_CREDITO|INVESTIMENTO)$", 
             message = "Tipo deve ser CORRENTE, POUPANCA, CARTAO_CREDITO ou INVESTIMENTO")
    private String type;
    
    @NotNull(message = "Saldo inicial é obrigatório")
    @DecimalMin(value = "-999999999.99", message = "Saldo inicial muito baixo")
    @DecimalMax(value = "999999999.99", message = "Saldo inicial muito alto")
    private Double balance;
    
    @Min(value = 1, message = "Dia de fechamento deve estar entre 1 e 31")
    @Max(value = 31, message = "Dia de fechamento deve estar entre 1 e 31")
    private Integer diaFechamento;
    
    @Min(value = 1, message = "Dia de pagamento deve estar entre 1 e 31")
    @Max(value = 31, message = "Dia de pagamento deve estar entre 1 e 31")
    private Integer diaPagamento;
    
    public AccountRequest() {}
    
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public Double getBalance() {
        return balance;
    }
    
    public void setBalance(Double balance) {
        this.balance = balance;
    }
    
    public Integer getDiaFechamento() {
        return diaFechamento;
    }
    
    public void setDiaFechamento(Integer diaFechamento) {
        this.diaFechamento = diaFechamento;
    }
    
    public Integer getDiaPagamento() {
        return diaPagamento;
    }
    
    public void setDiaPagamento(Integer diaPagamento) {
        this.diaPagamento = diaPagamento;
    }
}






