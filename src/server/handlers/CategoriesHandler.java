// NOTA: Por enquanto sem package para compatibilidade com entidades no default package
// TODO: Mover entidades (BancoDados, Categoria, etc) para um package próprio

import com.sun.net.httpserver.*;
import java.io.*;
import java.util.*;
import server.utils.*;

/**
 * Handler para operações com Categorias
 * GET, POST, PUT, DELETE /api/categories
 */
public class CategoriesHandler implements HttpHandler {
    private BancoDadosPostgreSQL bancoDados;
    
    public CategoriesHandler(BancoDadosPostgreSQL bancoDados) {
        this.bancoDados = bancoDados;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        
        switch (method) {
            case "GET":
                handleGet(exchange);
                break;
            case "POST":
                handlePost(exchange);
                break;
            case "PUT":
                handlePut(exchange);
                break;
            case "DELETE":
                handleDelete(exchange);
                break;
            default:
                ResponseUtil.sendErrorResponse(exchange, 405, "Método não permitido");
        }
    }
    
    private void handleGet(HttpExchange exchange) throws IOException {
        try {
            String userIdParam = RequestUtil.getQueryParam(exchange, "userId");
            int userId = userIdParam != null ? Integer.parseInt(userIdParam) : 1;
            
            List<Categoria> categories = bancoDados.buscarCategoriasPorUsuario(userId);
            List<Map<String, Object>> categoryList = new ArrayList<>();
            
            for (Categoria categoria : categories) {
                Map<String, Object> categoryData = new HashMap<>();
                categoryData.put("idCategoria", categoria.getIdCategoria());
                categoryData.put("nome", categoria.getNome());
                categoryData.put("idUsuario", categoria.getIdUsuario());
                categoryList.add(categoryData);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", categoryList);
            
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
            String userIdStr = data.get("userId");
            int userId = (userIdStr != null && !userIdStr.isEmpty()) ? Integer.parseInt(userIdStr) : 1;
            
            int categoryId = bancoDados.cadastrarCategoria(name, userId);
            
            // Verifica se foi enviado um orçamento
            String budgetStr = data.get("budget");
            if (budgetStr != null && !budgetStr.isEmpty() && !budgetStr.equals("null")) {
                try {
                    double budgetValue = Double.parseDouble(budgetStr);
                    if (budgetValue > 0) {
                        // Por padrão, definimos como mensal se não especificado
                        bancoDados.cadastrarOrcamento(budgetValue, "MENSAL", categoryId, userId);
                    }
                } catch (NumberFormatException e) {
                    // Ignora erro de conversão, apenas cria a categoria sem orçamento
                    System.err.println("Erro ao converter orçamento: " + budgetStr);
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Categoria criada com sucesso");
            response.put("categoryId", categoryId);
            
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
            
            int categoryId = Integer.parseInt(data.get("id"));
            String newName = data.get("name");
            if (newName == null) newName = data.get("nome");
            
            bancoDados.atualizarCategoria(categoryId, newName);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Categoria atualizada com sucesso");
            
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
                ResponseUtil.sendErrorResponse(exchange, 400, "ID da categoria não fornecido");
                return;
            }
            
            int categoryId = Integer.parseInt(idParam);
            bancoDados.excluirCategoria(categoryId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Categoria excluída com sucesso");
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            
            ResponseUtil.sendJsonResponse(exchange, 400, response);
        }
    }
}

