package server.handlers;

import com.sun.net.httpserver.*;
import java.io.*;
import java.util.*;
import server.model.Conta;
import server.utils.*;
import server.utils.CreditCardUtil;
import server.validation.*;
import server.repository.*;

/**
 * Handler para operações com Contas
 */
public class AccountsHandler implements HttpHandler {
    private final AccountRepository accountRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    
    public AccountsHandler() {
        this.accountRepository = new AccountRepository();
        this.expenseRepository = new ExpenseRepository();
        this.incomeRepository = new IncomeRepository();
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        
        // Verifica se é uma requisição para informações da fatura
        if (method.equals("GET") && path.contains("/invoice-info")) {
            handleGetInvoiceInfo(exchange);
            return;
        }
        
        switch (method) {
            case "GET": handleGet(exchange); break;
            case "POST": handlePost(exchange); break;
            case "PUT": handlePut(exchange); break;
            case "DELETE": handleDelete(exchange); break;
            default: ResponseUtil.sendErrorResponse(exchange, 405, "Método não permitido");
        }
    }
    
    private void handleGet(HttpExchange exchange) throws IOException {
        try {
            String userIdParam = RequestUtil.getQueryParam(exchange, "userId");
            int userId = userIdParam != null ? Integer.parseInt(userIdParam) : 1;
            
            List<Conta> accounts = accountRepository.buscarContasPorUsuario(userId);
            List<Map<String, Object>> accountList = new ArrayList<>();
            
            for (Conta conta : accounts) {
                Map<String, Object> accountData = new HashMap<>();
                accountData.put("idConta", conta.getIdConta());
                accountData.put("nome", conta.getNome());
                accountData.put("tipo", conta.getTipo());
                
                // Para contas de investimento, calcula o saldo baseado no valor atual dos investimentos
                String tipoConta = conta.getTipo() != null ? conta.getTipo().toLowerCase().trim() : "";
                if (tipoConta.equals("investimento") || tipoConta.equals("investimento (corretora)") || tipoConta.startsWith("investimento")) {
                    double valorAtualInvestimentos = accountRepository.calcularValorAtualInvestimentos(conta.getIdConta());
                    accountData.put("saldoAtual", valorAtualInvestimentos);
                } else {
                accountData.put("saldoAtual", conta.getSaldoAtual());
                }
                
                accountData.put("idUsuario", conta.getIdUsuario());
                if (conta.getDiaFechamento() != null) {
                    accountData.put("diaFechamento", conta.getDiaFechamento());
                }
                if (conta.getDiaPagamento() != null) {
                    accountData.put("diaPagamento", conta.getDiaPagamento());
                }
                
                // Se for cartão de crédito, calcula informações da fatura
                if (conta.isCartaoCredito() && conta.getDiaFechamento() != null && conta.getDiaPagamento() != null) {
                    try {
                        Map<String, Object> faturaInfo = CreditCardUtil.calcularInfoFatura(
                            conta.getDiaFechamento(), 
                            conta.getDiaPagamento()
                        );
                        
                        java.time.LocalDate ultimoFechamento = java.time.LocalDate.parse((String) faturaInfo.get("ultimoFechamento"));
                        java.time.LocalDate proximoFechamento = java.time.LocalDate.parse((String) faturaInfo.get("proximoFechamento"));
                        
                        double valorFaturaAtual = expenseRepository.calcularValorFaturaAtual(
                            conta.getIdConta(), 
                            ultimoFechamento, 
                            proximoFechamento
                        );
                        
                        double totalJaPago = incomeRepository.calcularTotalReceitasPorPeriodoEConta(
                            userId, 
                            conta.getIdConta(), 
                            ultimoFechamento, 
                            proximoFechamento
                        );
                        
                        double valorDisponivelParaPagamento = valorFaturaAtual - totalJaPago;
                        // Garante que o valor disponível nunca seja negativo
                        if (valorDisponivelParaPagamento < 0) {
                            valorDisponivelParaPagamento = 0;
                        }
                        
                        accountData.put("valorFatura", valorFaturaAtual);
                        accountData.put("valorJaPago", totalJaPago);
                        accountData.put("valorDisponivelPagamento", valorDisponivelParaPagamento);
                        accountData.put("faturaInfo", faturaInfo);
                    } catch (Exception e) {
                        // Se houver erro ao calcular, não adiciona as informações
                    }
                }
                
                accountList.add(accountData);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", accountList);
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            ResponseUtil.sendErrorResponse(exchange, 500, "Erro interno do servidor");
        }
    }
    
    private void handleGetInvoiceInfo(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            // Extrai o ID da conta da URL (ex: /api/accounts/123/invoice-info)
            String[] pathParts = path.split("/");
            int accountId = -1;
            for (int i = 0; i < pathParts.length; i++) {
                if (pathParts[i].equals("accounts") && i + 1 < pathParts.length) {
                    try {
                        accountId = Integer.parseInt(pathParts[i + 1]);
                        break;
                    } catch (NumberFormatException e) {
                        // Continua procurando
                    }
                }
            }
            
            if (accountId <= 0) {
                ResponseUtil.sendErrorResponse(exchange, 400, "ID da conta inválido");
                return;
            }
            
            String userIdParam = RequestUtil.getQueryParam(exchange, "userId");
            int userId = userIdParam != null ? Integer.parseInt(userIdParam) : 1;
            
            Conta conta = accountRepository.buscarConta(accountId);
            if (conta == null) {
                ResponseUtil.sendErrorResponse(exchange, 404, "Conta não encontrada");
                return;
            }
            
            if (!conta.isCartaoCredito() || conta.getDiaFechamento() == null || conta.getDiaPagamento() == null) {
                ResponseUtil.sendErrorResponse(exchange, 400, "Esta conta não é um cartão de crédito ou não possui informações de fatura");
                return;
            }
            
            Map<String, Object> faturaInfo = CreditCardUtil.calcularInfoFatura(
                conta.getDiaFechamento(), 
                conta.getDiaPagamento()
            );
            
            java.time.LocalDate ultimoFechamento = java.time.LocalDate.parse((String) faturaInfo.get("ultimoFechamento"));
            java.time.LocalDate proximoFechamento = java.time.LocalDate.parse((String) faturaInfo.get("proximoFechamento"));
            
            double valorFaturaAtual = expenseRepository.calcularValorFaturaAtual(
                conta.getIdConta(), 
                ultimoFechamento, 
                proximoFechamento
            );
            
            double totalJaPago = incomeRepository.calcularTotalReceitasPorPeriodoEConta(
                userId, 
                conta.getIdConta(), 
                ultimoFechamento, 
                proximoFechamento
            );
            
            double valorDisponivelParaPagamento = valorFaturaAtual - totalJaPago;
            // Garante que o valor disponível nunca seja negativo
            if (valorDisponivelParaPagamento < 0) {
                valorDisponivelParaPagamento = 0;
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", Map.of(
                "valorFatura", valorFaturaAtual,
                "valorJaPago", totalJaPago,
                "valorDisponivelPagamento", valorDisponivelParaPagamento
            ));
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Erro ao calcular informações da fatura: " + e.getMessage());
            ResponseUtil.sendJsonResponse(exchange, 500, response);
        }
    }
    
    private void handlePost(HttpExchange exchange) throws IOException {
        try {
            String requestBody = RequestUtil.readRequestBody(exchange);
            Map<String, Object> data = JsonUtil.parseJsonWithNested(requestBody);
            
            // Validações robustas
            ValidationResult validation = new ValidationResult();
            
            String name = null;
            Object nameObj = data.get("name");
            if (nameObj != null) name = nameObj.toString();
            if (name == null) {
                Object nomeObj = data.get("nome");
                if (nomeObj != null) name = nomeObj.toString();
            }
            validation.addErrors(InputValidator.validateName("Nome da conta", name, true).getErrors());
            
            String type = null;
            Object typeObj = data.get("type");
            if (typeObj != null) type = typeObj.toString();
            if (type == null) {
                Object tipoObj = data.get("tipo");
                if (tipoObj != null) type = tipoObj.toString();
            }
            String[] tiposPermitidos = {
                "CORRENTE", "POUPANCA", "POUPANÇA", "INVESTIMENTO", 
                "INVESTIMENTO (CORRETORA)", "CARTAO_CREDITO", "CARTAO DE CREDITO", 
                "CARTAO DE CRÉDITO", "CARTÃO DE CRÉDITO", "CARTÃO DE CREDITO", 
                "OUTROS", "DINHEIRO", "Corrente", "Poupança", "Investimento", 
                "Cartão de Crédito", "Dinheiro"
            };
            validation.addErrors(InputValidator.validateEnum("Tipo da conta", type, tiposPermitidos, true).getErrors());
            
            // Obtém saldo - pode vir como Number ou String
            String balanceStr = null;
            Object balanceObj = data.get("balance");
            if (balanceObj == null) balanceObj = data.get("saldoInicial");
            
            if (balanceObj != null) {
                if (balanceObj instanceof Number) {
                    balanceStr = balanceObj.toString();
                } else {
                    balanceStr = balanceObj.toString();
                }
            }
            validation.addErrors(InputValidator.validateMoneyString("Saldo inicial", balanceStr, true).getErrors());
            
            // Lê campos opcionais de cartão de crédito
            Integer diaFechamento = null;
            Integer diaPagamento = null;
            Object diaFechamentoObj = data.get("diaFechamento");
            if (diaFechamentoObj != null) {
                try {
                    if (diaFechamentoObj instanceof Number) {
                        diaFechamento = ((Number) diaFechamentoObj).intValue();
                    } else {
                        String diaFechamentoStr = diaFechamentoObj.toString();
                        if (!diaFechamentoStr.isEmpty()) {
                            diaFechamento = Integer.parseInt(diaFechamentoStr);
                        }
                    }
                    if (diaFechamento != null) {
                        validation.addErrors(InputValidator.validateDayOfMonth("Dia de fechamento", diaFechamento, false).getErrors());
                    }
                } catch (NumberFormatException e) {
                    validation.addError("Dia de fechamento deve ser um número válido");
                }
            }
            Object diaPagamentoObj = data.get("diaPagamento");
            if (diaPagamentoObj != null) {
                try {
                    if (diaPagamentoObj instanceof Number) {
                        diaPagamento = ((Number) diaPagamentoObj).intValue();
                    } else {
                        String diaPagamentoStr = diaPagamentoObj.toString();
                        if (!diaPagamentoStr.isEmpty()) {
                            diaPagamento = Integer.parseInt(diaPagamentoStr);
                        }
                    }
                    if (diaPagamento != null) {
                        validation.addErrors(InputValidator.validateDayOfMonth("Dia de pagamento", diaPagamento, false).getErrors());
                    }
                } catch (NumberFormatException e) {
                    validation.addError("Dia de pagamento deve ser um número válido");
                }
            }
            
            // Se houver erros de validação, retorna
            if (!validation.isValid()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", validation.getErrorMessage());
                ResponseUtil.sendJsonResponse(exchange, 400, response);
                return;
            }
            
            // Sanitiza e converte valores
            name = InputValidator.sanitizeInput(name);
            double balance;
            try {
                // Se já veio como Number, usa diretamente
                if (balanceObj instanceof Number) {
                    balance = ((Number) balanceObj).doubleValue();
                } else {
                    // Se veio como String, usa NumberUtil para tratar formato brasileiro (aceita vírgula e ponto)
                    balance = NumberUtil.parseDoubleBrazilian(balanceStr);
                }
            } catch (NumberFormatException e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Saldo inicial deve ser um número válido: " + e.getMessage());
                ResponseUtil.sendJsonResponse(exchange, 400, response);
                return;
            }
            
            // Obtém o ID do usuário autenticado do token JWT
            int userId;
            try {
                userId = AuthUtil.requireUserId(exchange);
            } catch (AuthUtil.UnauthorizedException e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Usuário não autenticado");
                ResponseUtil.sendJsonResponse(exchange, 401, response);
                return;
            }
            
            // Valida se o userId do body (se presente) corresponde ao do token
            Object userIdBodyObj = data.get("userId");
            if (userIdBodyObj != null) {
                int userIdBody;
                try {
                    if (userIdBodyObj instanceof Number) {
                        userIdBody = ((Number) userIdBodyObj).intValue();
                    } else {
                        userIdBody = Integer.parseInt(userIdBodyObj.toString());
                    }
                    
                    if (userIdBody != userId) {
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", false);
                        response.put("message", "Token de autenticação desatualizado. Faça logout e login novamente. (Token: " + userId + ", Enviado: " + userIdBody + ")");
                        ResponseUtil.sendJsonResponse(exchange, 401, response);
                        return;
                    }
                } catch (NumberFormatException e) {
                    // Ignora se userId do body não for um número válido
                }
            }
            
            int accountId = accountRepository.cadastrarConta(name, type, balance, userId, diaFechamento, diaPagamento);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Conta criada com sucesso");
            response.put("accountId", accountId);
            
            ResponseUtil.sendJsonResponse(exchange, 201, response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            ResponseUtil.sendJsonResponse(exchange, 400, response);
        }
    }
    
    private void handlePut(HttpExchange exchange) throws IOException {
        try {
            String requestBody = RequestUtil.readRequestBody(exchange);
            Map<String, Object> data = JsonUtil.parseJsonWithNested(requestBody);
            
            // Validações robustas
            ValidationResult validation = new ValidationResult();
            
            String idStr = null;
            Object idObj = data.get("id");
            if (idObj != null) idStr = idObj.toString();
            
            // Se não encontrou no body, tenta pegar da URL
            if (idStr == null) {
                String path = exchange.getRequestURI().getPath();
                String[] segments = path.split("/");
                if (segments.length > 0) {
                    try {
                        // Tenta o último segmento
                        String lastSegment = segments[segments.length - 1];
                        // Verifica se é número
                        Integer.parseInt(lastSegment);
                        idStr = lastSegment;
                    } catch (NumberFormatException e) {
                        // Ignora se não for número
                    }
                }
            }
            
            Integer accountId = null;
            if (idStr != null) {
                try {
                    accountId = Integer.parseInt(idStr);
                    validation.addErrors(InputValidator.validateId("ID da conta", accountId, true).getErrors());
                } catch (NumberFormatException e) {
                    validation.addError("ID da conta deve ser um número válido");
                }
            } else {
                validation.addError("ID da conta é obrigatório");
            }
            
            String name = null;
            Object nameObj = data.get("name");
            if (nameObj != null) name = nameObj.toString();
            if (name == null) {
                Object nomeObj = data.get("nome");
                if (nomeObj != null) name = nomeObj.toString();
            }
            validation.addErrors(InputValidator.validateName("Nome da conta", name, true).getErrors());
            
            String type = null;
            Object typeObj = data.get("type");
            if (typeObj != null) type = typeObj.toString();
            if (type == null) {
                Object tipoObj = data.get("tipo");
                if (tipoObj != null) type = tipoObj.toString();
            }
            String[] tiposPermitidos = {
                "CORRENTE", "POUPANCA", "POUPANÇA", "INVESTIMENTO", 
                "INVESTIMENTO (CORRETORA)", "CARTAO_CREDITO", "CARTAO DE CREDITO", 
                "CARTAO DE CRÉDITO", "CARTÃO DE CRÉDITO", "CARTÃO DE CREDITO", 
                "OUTROS", "DINHEIRO", "Corrente", "Poupança", "Investimento", 
                "Cartão de Crédito", "Dinheiro"
            };
            validation.addErrors(InputValidator.validateEnum("Tipo da conta", type, tiposPermitidos, true).getErrors());
            
            // Obtém saldo - pode vir como Number ou String
            String balanceStr = null;
            Object balanceObj = data.get("balance");
            if (balanceObj == null) balanceObj = data.get("saldoInicial");
            
            if (balanceObj != null) {
                if (balanceObj instanceof Number) {
                    balanceStr = balanceObj.toString();
                } else {
                    balanceStr = balanceObj.toString();
                }
            }
            validation.addErrors(InputValidator.validateMoneyString("Saldo inicial", balanceStr, true).getErrors());
            
            // Lê campos opcionais de cartão de crédito
            Integer diaFechamento = null;
            Integer diaPagamento = null;
            Object diaFechamentoObj = data.get("diaFechamento");
            if (diaFechamentoObj != null) {
                try {
                    if (diaFechamentoObj instanceof Number) {
                        diaFechamento = ((Number) diaFechamentoObj).intValue();
                    } else {
                        String diaFechamentoStr = diaFechamentoObj.toString();
                        if (!diaFechamentoStr.isEmpty()) {
                            diaFechamento = Integer.parseInt(diaFechamentoStr);
                        }
                    }
                    if (diaFechamento != null) {
                        validation.addErrors(InputValidator.validateDayOfMonth("Dia de fechamento", diaFechamento, false).getErrors());
                    }
                } catch (NumberFormatException e) {
                    validation.addError("Dia de fechamento deve ser um número válido");
                }
            }
            Object diaPagamentoObj = data.get("diaPagamento");
            if (diaPagamentoObj != null) {
                try {
                    if (diaPagamentoObj instanceof Number) {
                        diaPagamento = ((Number) diaPagamentoObj).intValue();
                    } else {
                        String diaPagamentoStr = diaPagamentoObj.toString();
                        if (!diaPagamentoStr.isEmpty()) {
                            diaPagamento = Integer.parseInt(diaPagamentoStr);
                        }
                    }
                    if (diaPagamento != null) {
                        validation.addErrors(InputValidator.validateDayOfMonth("Dia de pagamento", diaPagamento, false).getErrors());
                    }
                } catch (NumberFormatException e) {
                    validation.addError("Dia de pagamento deve ser um número válido");
                }
            }
            
            // Se houver erros de validação, retorna
            if (!validation.isValid()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", validation.getErrorMessage());
                ResponseUtil.sendJsonResponse(exchange, 400, response);
                return;
            }
            
            // Sanitiza e converte valores
            name = InputValidator.sanitizeInput(name);
            double balance;
            try {
                // Se já veio como Number, usa diretamente
                if (balanceObj instanceof Number) {
                    balance = ((Number) balanceObj).doubleValue();
                } else {
                    // Se veio como String, usa NumberUtil para tratar formato brasileiro (aceita vírgula e ponto)
                    balance = NumberUtil.parseDoubleBrazilian(balanceStr);
                }
            } catch (NumberFormatException e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Saldo inicial deve ser um número válido: " + e.getMessage());
                ResponseUtil.sendJsonResponse(exchange, 400, response);
                return;
            }
            
            accountRepository.atualizarConta(accountId, name, type, balance, diaFechamento, diaPagamento);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Conta atualizada com sucesso");
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            ResponseUtil.sendJsonResponse(exchange, 400, response);
        }
    }
    
    private void handleDelete(HttpExchange exchange) throws IOException {
        try {
            String idParam = RequestUtil.getQueryParam(exchange, "id");
            
            // Se não encontrou no query param, tenta pegar da URL
            if (idParam == null) {
                String path = exchange.getRequestURI().getPath();
                String[] segments = path.split("/");
                if (segments.length > 0) {
                    try {
                        // Tenta o último segmento
                        String lastSegment = segments[segments.length - 1];
                        // Verifica se é número
                        Integer.parseInt(lastSegment);
                        idParam = lastSegment;
                    } catch (NumberFormatException e) {
                        // Ignora se não for número
                    }
                }
            }
            
            if (idParam == null) {
                ResponseUtil.sendErrorResponse(exchange, 400, "ID da conta não fornecido");
                return;
            }
            
            // Valida ID
            Integer accountId;
            try {
                accountId = Integer.parseInt(idParam);
                ValidationResult validation = InputValidator.validateId("ID da conta", accountId, true);
                if (!validation.isValid()) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", validation.getErrorMessage());
                    ResponseUtil.sendJsonResponse(exchange, 400, response);
                    return;
                }
            } catch (NumberFormatException e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "ID da conta deve ser um número válido");
                ResponseUtil.sendJsonResponse(exchange, 400, response);
                return;
            }
            
            accountRepository.excluirConta(accountId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Conta excluída com sucesso");
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            ResponseUtil.sendJsonResponse(exchange, 400, response);
        }
    }
}

