package server.handlers;

import com.sun.net.httpserver.*;
import java.io.*;
import java.util.*;
import server.model.Categoria;
import server.utils.*;
import server.validation.*;
import server.repository.*;

/**
 * Handler para operações com Categorias
 * GET, POST, PUT, DELETE /api/categories
 */
public class CategoriesHandler implements HttpHandler {
    private final CategoryRepository categoryRepository;
    
    public CategoriesHandler() {
        this.categoryRepository = new CategoryRepository();
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
            // Usa o userId do token JWT autenticado, não do parâmetro da query string
            // Isso previne que usuários vejam categorias de outros usuários
            int userId = AuthUtil.requireUserId(exchange);
            
            List<Categoria> categories = categoryRepository.buscarCategoriasPorUsuario(userId);
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
            validation.addErrors(InputValidator.validateName("Nome da categoria", name, true).getErrors());
            
            // Obtém o ID do usuário autenticado do token JWT
            // NÃO aceita userId do body da requisição para prevenir que usuários criem categorias para outros
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
            
            if (!validation.isValid()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", validation.getErrorMessage());
                ResponseUtil.sendJsonResponse(exchange, 400, response);
                return;
            }
            
            // Sanitiza nome
            name = InputValidator.sanitizeInput(name);
            
            // Verifica se foi enviado um orçamento
            String budgetStr = null;
            Object budgetObj = data.get("budget");
            if (budgetObj != null) budgetStr = budgetObj.toString();
            int categoryId;
            
            if (budgetStr != null && !budgetStr.isEmpty() && !budgetStr.equals("null")) {
                try {
                    double budgetValue;
                    if (budgetObj instanceof Number) {
                        budgetValue = ((Number) budgetObj).doubleValue();
                    } else {
                        budgetValue = Double.parseDouble(budgetStr);
                    }
                    if (budgetValue > 0) {
                        // Valida valor do orçamento
                        ValidationResult budgetValidation = InputValidator.validateMoney("Valor do orçamento", budgetValue, true);
                        if (!budgetValidation.isValid()) {
                            Map<String, Object> response = new HashMap<>();
                            response.put("success", false);
                            response.put("message", budgetValidation.getErrorMessage());
                            ResponseUtil.sendJsonResponse(exchange, 400, response);
                            return;
                        }
                        
                        // Cadastra categoria e orçamento atomicamente na mesma transação
                        categoryId = categoryRepository.cadastrarCategoriaComOrcamento(name, userId, budgetValue, "MENSAL");
                    } else {
                        // Se o valor é 0 ou negativo, cadastra apenas a categoria
                        categoryId = categoryRepository.cadastrarCategoria(name, userId);
                    }
                } catch (NumberFormatException e) {
                    // Se não conseguir converter, cadastra apenas a categoria
                    categoryId = categoryRepository.cadastrarCategoria(name, userId);
                } catch (Exception e) {
                    // Se houver erro ao cadastrar com orçamento, retorna erro
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "Erro ao cadastrar categoria com orçamento: " + e.getMessage());
                    ResponseUtil.sendJsonResponse(exchange, 400, response);
                    return;
                }
            } else {
                // Sem orçamento, cadastra apenas a categoria
                categoryId = categoryRepository.cadastrarCategoria(name, userId);
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
            Map<String, Object> data = JsonUtil.parseJsonWithNested(requestBody);
            
            // Validações robustas
            ValidationResult validation = new ValidationResult();
            
            // Tenta obter ID do corpo da requisição (várias possibilidades)
            String idStr = null;
            Object idObj = data.get("id");
            if (idObj != null) idStr = idObj.toString();
            if (idStr == null) {
                Object idCategoriaObj = data.get("idCategoria");
                if (idCategoriaObj != null) idStr = idCategoriaObj.toString();
            }
            if (idStr == null) {
                Object categoryIdObj = data.get("categoryId");
                if (categoryIdObj != null) idStr = categoryIdObj.toString();
            }
            
            // Se não encontrou no corpo, tenta pegar da URL (query param ou path)
            if (idStr == null) {
                idStr = RequestUtil.getQueryParam(exchange, "id");
            }
            if (idStr == null) {
                idStr = RequestUtil.getQueryParam(exchange, "idCategoria");
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
            
            Integer categoryId = null;
            if (idStr != null) {
                try {
                    categoryId = Integer.parseInt(idStr);
                    validation.addErrors(InputValidator.validateId("ID da categoria", categoryId, true).getErrors());
                } catch (NumberFormatException e) {
                    validation.addError("ID da categoria deve ser um número válido");
                }
            } else {
                validation.addError("ID da categoria é obrigatório");
            }
            
            String newName = null;
            Object nameObj = data.get("name");
            if (nameObj != null) newName = nameObj.toString();
            if (newName == null) {
                Object nomeObj = data.get("nome");
                if (nomeObj != null) newName = nomeObj.toString();
            }
            validation.addErrors(InputValidator.validateName("Nome da categoria", newName, true).getErrors());
            
            if (!validation.isValid()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", validation.getErrorMessage());
                ResponseUtil.sendJsonResponse(exchange, 400, response);
                return;
            }
            
            // Sanitiza nome
            newName = InputValidator.sanitizeInput(newName);
            
            // Valida que o usuário está autenticado e que a categoria pertence a ele
            int authenticatedUserId = AuthUtil.requireUserId(exchange);
            Categoria categoria = categoryRepository.buscarCategoria(categoryId);
            if (categoria == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Categoria não encontrada");
                ResponseUtil.sendJsonResponse(exchange, 404, response);
                return;
            }
            
            if (categoria.getIdUsuario() != authenticatedUserId) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Você não tem permissão para atualizar esta categoria");
                ResponseUtil.sendJsonResponse(exchange, 403, response);
                return;
            }
            
            categoryRepository.atualizarCategoria(categoryId, newName);
            
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
            int authenticatedUserId = AuthUtil.requireUserId(exchange);
            
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
            
            // Verifica se a categoria existe e pertence ao usuário autenticado
            Categoria categoria = categoryRepository.buscarCategoria(categoryId);
            if (categoria == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Categoria não encontrada");
                ResponseUtil.sendJsonResponse(exchange, 404, response);
                return;
            }
            
            if (categoria.getIdUsuario() != authenticatedUserId) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Você não tem permissão para excluir esta categoria");
                ResponseUtil.sendJsonResponse(exchange, 403, response);
                return;
            }
            
            categoryRepository.excluirCategoria(categoryId);
            
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

