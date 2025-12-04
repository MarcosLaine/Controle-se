package server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import server.model.*;
import server.repository.*;
import server.utils.*;
import java.io.IOException;
import java.util.*;

public class BudgetsHandler implements HttpHandler {
    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;

    public BudgetsHandler() {
        this.budgetRepository = new BudgetRepository();
        this.categoryRepository = new CategoryRepository();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        
        if ("GET".equals(method)) {
            handleGetBudgets(exchange);
        } else if ("POST".equals(method)) {
            handleCreateBudget(exchange);
        } else if ("PUT".equals(method)) {
            handleUpdateBudget(exchange);
        } else if ("DELETE".equals(method)) {
            handleDeleteBudget(exchange);
        } else {
            ResponseUtil.sendErrorResponse(exchange, 405, "Método não permitido");
        }
    }
    
    private void handleGetBudgets(HttpExchange exchange) throws IOException {
        try {
            // Usa o userId do token JWT autenticado, não do parâmetro da query string
            // Isso previne que usuários vejam orçamentos de outros usuários
            int userId = AuthUtil.requireUserId(exchange);
            
            List<Orcamento> budgets = budgetRepository.buscarOrcamentosPorUsuario(userId);
            List<Map<String, Object>> budgetList = new ArrayList<>();
            
            List<Categoria> allCategories = categoryRepository.buscarCategoriasPorUsuario(userId);
            Map<Integer, Categoria> categoryMap = new HashMap<>();
            for (Categoria cat : allCategories) {
                categoryMap.put(cat.getIdCategoria(), cat);
            }
            
            for (Orcamento orcamento : budgets) {
                Categoria categoria = categoryMap.get(orcamento.getIdCategoria());
                double spent = categoryRepository.calcularTotalGastosPorCategoriaEUsuario(orcamento.getIdCategoria(), userId);
                double percentageUsed = orcamento.getValorPlanejado() > 0 ? 
                    (spent / orcamento.getValorPlanejado()) * 100 : 0;
                
                percentageUsed = Math.min(percentageUsed, 100);
                percentageUsed = Math.round(percentageUsed * 100.0) / 100.0;
                
                Map<String, Object> budgetData = new HashMap<>();
                budgetData.put("idOrcamento", orcamento.getIdOrcamento());
                budgetData.put("idCategoria", orcamento.getIdCategoria());
                budgetData.put("valorPlanejado", orcamento.getValorPlanejado());
                budgetData.put("valorUsado", spent);
                budgetData.put("periodo", orcamento.getPeriodo());
                budgetData.put("categoryName", categoria != null ? categoria.getNome() : "Sem categoria");
                budgetData.put("percentageUsed", percentageUsed);
                budgetList.add(budgetData);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", budgetList);
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            e.printStackTrace();
            ResponseUtil.sendErrorResponse(exchange, 500, "Erro interno do servidor");
        }
    }
    
    private void handleCreateBudget(HttpExchange exchange) throws IOException {
        try {
            int userId = AuthUtil.requireUserId(exchange);
            String requestBody = RequestUtil.readRequestBody(exchange);
            Map<String, Object> data = JsonUtil.parseJsonWithNested(requestBody);
            
            // Obtém categoryId - pode vir como Number ou String
            Object categoryIdObj = data.get("categoryId");
            if (categoryIdObj == null) {
                throw new IllegalArgumentException("ID da categoria é obrigatório");
            }
            int categoryId;
            if (categoryIdObj instanceof Number) {
                categoryId = ((Number) categoryIdObj).intValue();
            } else {
                String categoryIdStr = categoryIdObj.toString();
                if (categoryIdStr == null || categoryIdStr.trim().isEmpty()) {
                    throw new IllegalArgumentException("ID da categoria é obrigatório");
                }
                categoryId = Integer.parseInt(categoryIdStr);
            }
            
            // Obtém valor - pode vir como Number ou String
            Object valueObj = data.get("value");
            if (valueObj == null) {
                throw new IllegalArgumentException("Valor é obrigatório");
            }
            double value;
            if (valueObj instanceof Number) {
                value = ((Number) valueObj).doubleValue();
            } else {
                String valueStr = valueObj.toString();
                if (valueStr == null || valueStr.trim().isEmpty() || valueStr.equals("null") || valueStr.equals("NaN")) {
                    throw new IllegalArgumentException("Valor é obrigatório");
                }
                value = NumberUtil.parseDoubleBrazilian(valueStr);
            }
            
            // Obtém período
            String period = null;
            Object periodObj = data.get("period");
            if (periodObj != null) {
                period = periodObj.toString();
            }
            if (period == null || period.trim().isEmpty()) {
                period = "MENSAL"; // Default
            }
            
            int budgetId = budgetRepository.cadastrarOrcamento(value, period, categoryId, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Orçamento criado com sucesso");
            response.put("budgetId", budgetId);
            
            ResponseUtil.sendJsonResponse(exchange, 201, response);
        } catch (AuthUtil.UnauthorizedException e) {
            ResponseUtil.sendErrorResponse(exchange, 401, e.getMessage());
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            ResponseUtil.sendJsonResponse(exchange, 400, response);
        }
    }
    
    private void handleUpdateBudget(HttpExchange exchange) throws IOException {
        try {
            int authenticatedUserId = AuthUtil.requireUserId(exchange);
            String requestBody = RequestUtil.readRequestBody(exchange);
            Map<String, Object> data = JsonUtil.parseJsonWithNested(requestBody);
            
            // Tenta obter ID do corpo ou da URL
            String idStr = null;
            Object idObj = data.get("id");
            if (idObj != null) idStr = idObj.toString();
            if (idStr == null) {
                idStr = RequestUtil.getQueryParam(exchange, "id");
            }
            if (idStr == null) {
                String path = exchange.getRequestURI().getPath();
                String[] segments = path.split("/");
                if (segments.length > 0) {
                    try {
                        String lastSegment = segments[segments.length - 1];
                        Integer.parseInt(lastSegment);
                        idStr = lastSegment;
                    } catch (NumberFormatException e) {
                        // Ignora se não for número
                    }
                }
            }
            
            if (idStr == null) {
                throw new IllegalArgumentException("ID do orçamento é obrigatório");
            }
            int budgetId = Integer.parseInt(idStr);
            
            // Verifica se o orçamento existe e pertence ao usuário autenticado
            Orcamento orcamento = budgetRepository.buscarOrcamento(budgetId);
            if (orcamento == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Orçamento não encontrado");
                ResponseUtil.sendJsonResponse(exchange, 404, response);
                return;
            }
            
            if (orcamento.getIdUsuario() != authenticatedUserId) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Você não tem permissão para atualizar este orçamento");
                ResponseUtil.sendJsonResponse(exchange, 403, response);
                return;
            }
            
            // Obtém valor - pode vir como Number ou String
            Object valueObj = data.get("value");
            if (valueObj == null) {
                throw new IllegalArgumentException("Valor é obrigatório");
            }
            double value;
            if (valueObj instanceof Number) {
                value = ((Number) valueObj).doubleValue();
            } else {
                String valueStr = valueObj.toString();
                if (valueStr == null || valueStr.trim().isEmpty() || valueStr.equals("null") || valueStr.equals("NaN")) {
                    throw new IllegalArgumentException("Valor é obrigatório");
                }
                value = NumberUtil.parseDoubleBrazilian(valueStr);
            }
            
            // Obtém período
            String period = null;
            Object periodObj = data.get("period");
            if (periodObj != null) {
                period = periodObj.toString();
            }
            if (period == null || period.trim().isEmpty()) {
                period = "MENSAL"; // Default
            }
            
            budgetRepository.atualizarOrcamento(budgetId, value, period);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Orçamento atualizado com sucesso");
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (AuthUtil.UnauthorizedException e) {
            ResponseUtil.sendErrorResponse(exchange, 401, e.getMessage());
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            ResponseUtil.sendJsonResponse(exchange, 400, response);
        }
    }
    
    private void handleDeleteBudget(HttpExchange exchange) throws IOException {
        try {
            int authenticatedUserId = AuthUtil.requireUserId(exchange);
            
            String idParam = RequestUtil.getQueryParam(exchange, "id");
            
            if (idParam == null) {
                String path = exchange.getRequestURI().getPath();
                String[] segments = path.split("/");
                if (segments.length > 3) {
                    idParam = segments[segments.length - 1];
                }
            }
            
            if (idParam == null) {
                ResponseUtil.sendErrorResponse(exchange, 400, "ID do orçamento não fornecido");
                return;
            }
            
            int budgetId = Integer.parseInt(idParam);
            
            // Verifica se o orçamento existe e pertence ao usuário autenticado
            Orcamento orcamento = budgetRepository.buscarOrcamento(budgetId);
            if (orcamento == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Orçamento não encontrado");
                ResponseUtil.sendJsonResponse(exchange, 404, response);
                return;
            }
            
            if (orcamento.getIdUsuario() != authenticatedUserId) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Você não tem permissão para excluir este orçamento");
                ResponseUtil.sendJsonResponse(exchange, 403, response);
                return;
            }
            
            budgetRepository.excluirOrcamento(budgetId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Orçamento excluído com sucesso");
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            ResponseUtil.sendJsonResponse(exchange, 400, response);
        }
    }
}

