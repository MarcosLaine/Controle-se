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
            String type = data.get("type");
            double balance = Double.parseDouble(data.get("balance"));
            int userId = 1;
            
            int accountId = bancoDados.cadastrarConta(name, type, balance, userId);
            
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
            
            int accountId = Integer.parseInt(data.get("id"));
            String name = data.get("name");
            String type = data.get("type");
            double balance = Double.parseDouble(data.get("balance"));
            
            bancoDados.atualizarConta(accountId, name, type, balance);
            
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

