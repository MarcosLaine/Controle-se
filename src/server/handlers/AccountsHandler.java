import com.sun.net.httpserver.*;
import java.io.*;
import java.util.*;
import server.utils.*;

/**
 * Handler para operações com Contas
 */
public class AccountsHandler implements HttpHandler {
    private BancoDadosPostgreSQL bancoDados;
    
    public AccountsHandler(BancoDadosPostgreSQL bancoDados) {
        this.bancoDados = bancoDados;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        
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
            
            List<Conta> accounts = bancoDados.buscarContasPorUsuario(userId);
            List<Map<String, Object>> accountList = new ArrayList<>();
            
            for (Conta conta : accounts) {
                Map<String, Object> accountData = new HashMap<>();
                accountData.put("idConta", conta.getIdConta());
                accountData.put("nome", conta.getNome());
                accountData.put("tipo", conta.getTipo());
                accountData.put("saldoAtual", conta.getSaldoAtual());
                accountData.put("idUsuario", conta.getIdUsuario());
                if (conta.getDiaFechamento() != null) {
                    accountData.put("diaFechamento", conta.getDiaFechamento());
                }
                if (conta.getDiaPagamento() != null) {
                    accountData.put("diaPagamento", conta.getDiaPagamento());
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
    
    private void handlePost(HttpExchange exchange) throws IOException {
        try {
            String requestBody = RequestUtil.readRequestBody(exchange);
            Map<String, String> data = JsonUtil.parseJson(requestBody);
            
            String name = data.get("name");
            if (name == null) name = data.get("nome");

            String type = data.get("type");
            if (type == null) type = data.get("tipo");
            
            String balanceStr = data.get("balance");
            if (balanceStr == null) {
                balanceStr = data.get("saldoInicial");
            }
            if (balanceStr == null) throw new IllegalArgumentException("Saldo é obrigatório");
            double balance = Double.parseDouble(balanceStr);
            int userId = 1;
            
            // Lê campos opcionais de cartão de crédito
            Integer diaFechamento = null;
            Integer diaPagamento = null;
            String diaFechamentoStr = data.get("diaFechamento");
            if (diaFechamentoStr != null && !diaFechamentoStr.isEmpty()) {
                diaFechamento = Integer.parseInt(diaFechamentoStr);
            }
            String diaPagamentoStr = data.get("diaPagamento");
            if (diaPagamentoStr != null && !diaPagamentoStr.isEmpty()) {
                diaPagamento = Integer.parseInt(diaPagamentoStr);
            }
            
            int accountId = bancoDados.cadastrarConta(name, type, balance, userId, diaFechamento, diaPagamento);
            
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
            Map<String, String> data = JsonUtil.parseJson(requestBody);
            
            String idStr = data.get("id");
            
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
            
            if (idStr == null) throw new IllegalArgumentException("ID é obrigatório");
            int accountId = Integer.parseInt(idStr);
            
            String name = data.get("name");
            if (name == null) name = data.get("nome");

            String type = data.get("type");
            if (type == null) type = data.get("tipo");
            
            String balanceStr = data.get("balance");
            if (balanceStr == null) {
                balanceStr = data.get("saldoInicial");
            }
            if (balanceStr == null) throw new IllegalArgumentException("Saldo é obrigatório");
            double balance = Double.parseDouble(balanceStr);
            
            // Lê campos opcionais de cartão de crédito
            Integer diaFechamento = null;
            Integer diaPagamento = null;
            String diaFechamentoStr = data.get("diaFechamento");
            if (diaFechamentoStr != null && !diaFechamentoStr.isEmpty()) {
                diaFechamento = Integer.parseInt(diaFechamentoStr);
            }
            String diaPagamentoStr = data.get("diaPagamento");
            if (diaPagamentoStr != null && !diaPagamentoStr.isEmpty()) {
                diaPagamento = Integer.parseInt(diaPagamentoStr);
            }
            
            bancoDados.atualizarConta(accountId, name, type, balance, diaFechamento, diaPagamento);
            
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
            
            int accountId = Integer.parseInt(idParam);
            bancoDados.excluirConta(accountId);
            
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

