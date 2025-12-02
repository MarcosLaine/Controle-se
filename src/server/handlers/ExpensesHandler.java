package server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import server.model.*;
import server.repository.*;
import server.utils.*;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

public class ExpensesHandler implements HttpHandler {
    private final ExpenseRepository expenseRepository;
    private final AccountRepository accountRepository;
    private final TagRepository tagRepository;

    public ExpensesHandler() {
        this.expenseRepository = new ExpenseRepository();
        this.accountRepository = new AccountRepository();
        this.tagRepository = new TagRepository();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            if (method != null) {
                method = method.toUpperCase().trim();
            }
            if ("OPTIONS".equals(method)) {
                handleOptions(exchange);
            } else if ("POST".equals(method)) {
                handlePost(exchange);
            } else if ("DELETE".equals(method)) {
                handleDelete(exchange);
            } else {
                ResponseUtil.sendErrorResponse(exchange, 405, "Método não permitido: " + method);
            }
        } catch (AuthUtil.UnauthorizedException e) {
            ResponseUtil.sendErrorResponse(exchange, 401, e.getMessage());
        }
    }
    
    private void handleOptions(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.sendResponseHeaders(200, -1);
        exchange.close();
    }
    
    private void handlePost(HttpExchange exchange) throws IOException {
        try {
            int userId = AuthUtil.requireUserId(exchange);
            String requestBody = RequestUtil.readRequestBody(exchange);
            Map<String, Object> data = JsonUtil.parseJsonWithNested(requestBody);
            data.put("userId", userId);
            
            String description = (String) data.get("description");
            double value = ((Number) data.get("value")).doubleValue();
            LocalDate date = LocalDate.parse((String) data.get("date"));
            int accountId = ((Number) data.get("accountId")).intValue();
            String frequency = (String) data.get("frequency");
            
            List<Integer> categoryIds = parseCategoryIds(data);
            List<Integer> tagIds = parseTagIds(data);
            String[] observacoes = parseObservacoes(data);
            
            Conta conta = accountRepository.buscarConta(accountId);
            if (conta == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Conta não encontrada");
                ResponseUtil.sendJsonResponse(exchange, 400, response);
                return;
            }
            
            String tipoConta = conta.getTipo() != null ? conta.getTipo().toLowerCase().trim() : "";
            if (tipoConta.equals("investimento") || tipoConta.equals("investimento (corretora)") || tipoConta.startsWith("investimento")) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Contas de investimento não podem ser usadas para gastos");
                ResponseUtil.sendJsonResponse(exchange, 400, response);
                return;
            }
            
            int expenseId = expenseRepository.cadastrarGasto(description, value, date, frequency, userId, categoryIds, accountId, observacoes);
            
            for (int tagId : tagIds) {
                tagRepository.associarTagTransacao(expenseId, "GASTO", tagId);
            }
            
            CacheUtil.invalidateCache("overview_" + userId);
            CacheUtil.invalidateCache("categories_" + userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Gasto registrado com sucesso");
            response.put("expenseId", expenseId);
            
            ResponseUtil.sendJsonResponse(exchange, 201, response);
        } catch (AuthUtil.UnauthorizedException e) {
            ResponseUtil.sendErrorResponse(exchange, 401, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Erro: " + e.getMessage());
            ResponseUtil.sendJsonResponse(exchange, 400, response);
        }
    }
    
    private void handleDelete(HttpExchange exchange) throws IOException {
        try {
            AuthUtil.requireUserId(exchange);
            String idParam = RequestUtil.getQueryParam(exchange, "id");
            
            if (idParam == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "ID do gasto não fornecido");
                ResponseUtil.sendJsonResponse(exchange, 400, response);
                return;
            }
            
            int expenseId = Integer.parseInt(idParam);
            Gasto gasto = expenseRepository.buscarGasto(expenseId);
            int userId = gasto != null ? gasto.getIdUsuario() : AuthUtil.requireUserId(exchange);
            
            expenseRepository.excluirGasto(expenseId);
            
            CacheUtil.invalidateCache("overview_" + userId);
            CacheUtil.invalidateCache("categories_" + userId);
            CacheUtil.invalidateCache("totalExpense_" + userId);
            CacheUtil.invalidateCache("balance_" + userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Gasto excluído com sucesso");
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (AuthUtil.UnauthorizedException e) {
            ResponseUtil.sendErrorResponse(exchange, 401, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Erro: " + e.getMessage());
            ResponseUtil.sendJsonResponse(exchange, 400, response);
        }
    }
    
    private List<Integer> parseCategoryIds(Map<String, Object> data) {
        List<Integer> categoryIds = new ArrayList<>();
        Object categoryIdsObj = data.get("categoryIds");
        
        if (categoryIdsObj != null) {
            if (categoryIdsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> categoryList = (List<Object>) categoryIdsObj;
                for (Object catId : categoryList) {
                    if (catId instanceof Number) {
                        categoryIds.add(((Number) catId).intValue());
                    }
                }
            } else if (categoryIdsObj instanceof String) {
                String categoryIdsStr = (String) categoryIdsObj;
                categoryIdsStr = categoryIdsStr.replaceAll("[\\[\\]\\s]", "");
                for (String idStr : categoryIdsStr.split(",")) {
                    if (!idStr.isEmpty()) {
                        categoryIds.add(Integer.parseInt(idStr));
                    }
                }
            } else if (categoryIdsObj instanceof Number) {
                categoryIds.add(((Number) categoryIdsObj).intValue());
            }
        } else {
            Object categoryIdObj = data.get("categoryId");
            if (categoryIdObj instanceof Number) {
                categoryIds.add(((Number) categoryIdObj).intValue());
            } else if (categoryIdObj instanceof String) {
                categoryIds.add(Integer.parseInt((String) categoryIdObj));
            }
        }
        return categoryIds;
    }
    
    private List<Integer> parseTagIds(Map<String, Object> data) {
        List<Integer> tagIds = new ArrayList<>();
        Object tagIdsObj = data.get("tagIds");
        
        if (tagIdsObj != null) {
            if (tagIdsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> tagList = (List<Object>) tagIdsObj;
                for (Object tagId : tagList) {
                    if (tagId instanceof Number) {
                        tagIds.add(((Number) tagId).intValue());
                    }
                }
            } else if (tagIdsObj instanceof String) {
                String tagIdsStr = (String) tagIdsObj;
                tagIdsStr = tagIdsStr.replaceAll("[\\[\\]\\s]", "");
                for (String idStr : tagIdsStr.split(",")) {
                    if (!idStr.isEmpty()) {
                        tagIds.add(Integer.parseInt(idStr));
                    }
                }
            } else if (tagIdsObj instanceof Number) {
                tagIds.add(((Number) tagIdsObj).intValue());
            }
        }
        return tagIds;
    }
    
    private String[] parseObservacoes(Map<String, Object> data) {
        String[] observacoes = null;
        Object observacoesObj = data.get("observacoes");
        if (observacoesObj != null) {
            if (observacoesObj instanceof String) {
                String observacoesStr = (String) observacoesObj;
                if (!observacoesStr.trim().isEmpty()) {
                    observacoesStr = observacoesStr.replace("\\n", "\n");
                    String[] obsArray = observacoesStr.split("[\\r\\n]+");
                    List<String> obsList = new ArrayList<>();
                    for (String obs : obsArray) {
                        String trimmed = obs.trim();
                        if (!trimmed.isEmpty()) {
                            obsList.add(trimmed);
                        }
                    }
                    if (!obsList.isEmpty()) {
                        observacoes = obsList.toArray(new String[0]);
                    }
                }
            } else if (observacoesObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> obsList = (List<Object>) observacoesObj;
                List<String> obsStringList = new ArrayList<>();
                for (Object obs : obsList) {
                    if (obs != null) {
                        obsStringList.add(obs.toString().trim());
                    }
                }
                if (!obsStringList.isEmpty()) {
                    observacoes = obsStringList.toArray(new String[0]);
                }
            }
        }
        return observacoes;
    }
}

