package server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;

/**
 * DTO para requisição de criação/atualização de categoria
 */
public class CategoryRequest {
    
    private Integer id;
    
    @NotBlank(message = "Nome da categoria é obrigatório")
    @Size(max = 100, message = "Nome não pode exceder 100 caracteres")
    private String name;
    
    @DecimalMin(value = "0.0", message = "Orçamento não pode ser negativo")
    @DecimalMax(value = "999999999.99", message = "Orçamento muito alto")
    private Double budget;
    
    public CategoryRequest() {}
    
    public CategoryRequest(String name) {
        this.name = name;
    }
    
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
    
    public Double getBudget() {
        return budget;
    }
    
    public void setBudget(Double budget) {
        this.budget = budget;
    }
}






