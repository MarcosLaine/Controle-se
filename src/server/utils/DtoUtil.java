package server.utils;

import server.dto.*;
import java.util.Map;

/**
 * Utilitário para converter Maps em DTOs
 */
public class DtoUtil {
    
    /**
     * Converte Map para RegisterRequest
     */
    public static RegisterRequest toRegisterRequest(Map<String, String> data) {
        RegisterRequest request = new RegisterRequest();
        request.setName(data.get("name"));
        request.setEmail(data.get("email"));
        request.setPassword(data.get("password"));
        return request;
    }
    
    /**
     * Converte Map para LoginRequest
     */
    public static LoginRequest toLoginRequest(Map<String, String> data) {
        LoginRequest request = new LoginRequest();
        request.setEmail(data.get("email"));
        request.setPassword(data.get("password"));
        request.setCaptchaToken(data.get("captchaToken"));
        return request;
    }
    
    /**
     * Converte Map para AccountRequest
     */
    public static AccountRequest toAccountRequest(Map<String, Object> data) {
        AccountRequest request = new AccountRequest();
        
        // ID
        Object idObj = data.get("id");
        if (idObj != null) {
            if (idObj instanceof Number) {
                request.setId(((Number) idObj).intValue());
            } else {
                try {
                    request.setId(Integer.parseInt(idObj.toString()));
                } catch (NumberFormatException e) {
                    // Ignora
                }
            }
        }
        
        // Name (suporta "name" e "nome")
        Object nameObj = data.get("name");
        if (nameObj == null) {
            nameObj = data.get("nome");
        }
        if (nameObj != null) {
            request.setName(nameObj.toString());
        }
        
        // Type (suporta "type" e "tipo")
        Object typeObj = data.get("type");
        if (typeObj == null) {
            typeObj = data.get("tipo");
        }
        if (typeObj != null) {
            request.setType(typeObj.toString());
        }
        
        // Balance (suporta "balance", "saldo" e "saldoInicial")
        Object balanceObj = data.get("balance");
        if (balanceObj == null) {
            balanceObj = data.get("saldo");
        }
        if (balanceObj == null) {
            balanceObj = data.get("saldoInicial");
        }
        if (balanceObj != null) {
            if (balanceObj instanceof Number) {
                request.setBalance(((Number) balanceObj).doubleValue());
            } else {
                try {
                    String balanceStr = balanceObj.toString().trim();
                    request.setBalance(NumberUtil.parseDoubleBrazilian(balanceStr));
                } catch (NumberFormatException e) {
                    // Ignora - a validação será feita no handler
                }
            }
        }
        
        // Dia de fechamento
        Object diaFechamentoObj = data.get("diaFechamento");
        if (diaFechamentoObj != null) {
            if (diaFechamentoObj instanceof Number) {
                request.setDiaFechamento(((Number) diaFechamentoObj).intValue());
            } else {
                try {
                    request.setDiaFechamento(Integer.parseInt(diaFechamentoObj.toString()));
                } catch (NumberFormatException e) {
                    // Ignora
                }
            }
        }
        
        // Dia de pagamento
        Object diaPagamentoObj = data.get("diaPagamento");
        if (diaPagamentoObj != null) {
            if (diaPagamentoObj instanceof Number) {
                request.setDiaPagamento(((Number) diaPagamentoObj).intValue());
            } else {
                try {
                    request.setDiaPagamento(Integer.parseInt(diaPagamentoObj.toString()));
                } catch (NumberFormatException e) {
                    // Ignora
                }
            }
        }
        
        return request;
    }
    
    /**
     * Converte Map para CategoryRequest
     */
    public static CategoryRequest toCategoryRequest(Map<String, Object> data) {
        CategoryRequest request = new CategoryRequest();
        
        // ID
        Object idObj = data.get("id");
        if (idObj != null) {
            if (idObj instanceof Number) {
                request.setId(((Number) idObj).intValue());
            } else {
                try {
                    request.setId(Integer.parseInt(idObj.toString()));
                } catch (NumberFormatException e) {
                    // Ignora
                }
            }
        }
        
        // Name (suporta "name" e "nome")
        Object nameObj = data.get("name");
        if (nameObj == null) {
            nameObj = data.get("nome");
        }
        if (nameObj != null) {
            request.setName(nameObj.toString());
        }
        
        // Budget
        Object budgetObj = data.get("budget");
        if (budgetObj != null) {
            if (budgetObj instanceof Number) {
                request.setBudget(((Number) budgetObj).doubleValue());
            } else {
                try {
                    String budgetStr = budgetObj.toString().trim()
                        .replace(".", "")
                        .replace(",", ".");
                    request.setBudget(Double.parseDouble(budgetStr));
                } catch (NumberFormatException e) {
                    // Ignora
                }
            }
        }
        
        return request;
    }
}






