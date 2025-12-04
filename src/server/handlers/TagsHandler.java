package server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import server.model.*;
import server.repository.*;
import server.utils.*;
import java.io.IOException;
import java.util.*;

public class TagsHandler implements HttpHandler {
    private final TagRepository tagRepository;

    public TagsHandler() {
        this.tagRepository = new TagRepository();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        
        if ("GET".equals(method)) {
            handleGetTags(exchange);
        } else if ("POST".equals(method)) {
            handleCreateTag(exchange);
        } else if ("PUT".equals(method)) {
            handleUpdateTag(exchange);
        } else if ("DELETE".equals(method)) {
            handleDeleteTag(exchange);
        } else {
            ResponseUtil.sendErrorResponse(exchange, 405, "Método não permitido");
        }
    }
    
    private void handleGetTags(HttpExchange exchange) throws IOException {
        try {
            // Usa o userId do token JWT autenticado, não do parâmetro da query string
            // Isso previne que usuários vejam tags de outros usuários
            int userId = AuthUtil.requireUserId(exchange);
            
            List<Tag> tags = tagRepository.buscarTagsPorUsuario(userId);
            List<Map<String, Object>> tagList = new ArrayList<>();
            
            for (Tag tag : tags) {
                Map<String, Object> tagData = new HashMap<>();
                tagData.put("idTag", tag.getIdTag());
                tagData.put("nome", tag.getNome());
                tagData.put("cor", tag.getCor());
                tagData.put("idUsuario", tag.getIdUsuario());
                tagList.add(tagData);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", tagList);
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            e.printStackTrace();
            ResponseUtil.sendErrorResponse(exchange, 500, "Erro interno do servidor");
        }
    }
    
    private void handleCreateTag(HttpExchange exchange) throws IOException {
        try {
            int userId = AuthUtil.requireUserId(exchange);
            String requestBody = RequestUtil.readRequestBody(exchange);
            Map<String, String> data = JsonUtil.parseJson(requestBody);
            
            String nome = data.get("nome");
            String cor = data.get("cor");
            if (cor == null || cor.isEmpty()) {
                cor = "#6B7280";
            }
            int tagId = tagRepository.cadastrarTag(nome, cor, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Tag criada com sucesso");
            response.put("tagId", tagId);
            
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
    
    private void handleUpdateTag(HttpExchange exchange) throws IOException {
        try {
            int authenticatedUserId = AuthUtil.requireUserId(exchange);
            String requestBody = RequestUtil.readRequestBody(exchange);
            Map<String, String> data = JsonUtil.parseJson(requestBody);
            
            int tagId = Integer.parseInt(data.get("id"));
            
            // Verifica se a tag existe e pertence ao usuário autenticado
            Tag tag = tagRepository.buscarTag(tagId);
            if (tag == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Tag não encontrada");
                ResponseUtil.sendJsonResponse(exchange, 404, response);
                return;
            }
            
            if (tag.getIdUsuario() != authenticatedUserId) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Você não tem permissão para atualizar esta tag");
                ResponseUtil.sendJsonResponse(exchange, 403, response);
                return;
            }
            
            String nome = data.get("nome");
            String cor = data.get("cor");
            
            tagRepository.atualizarTag(tagId, nome, cor);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Tag atualizada com sucesso");
            
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
    
    private void handleDeleteTag(HttpExchange exchange) throws IOException {
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
                ResponseUtil.sendErrorResponse(exchange, 400, "ID da tag não fornecido");
                return;
            }
            
            int tagId = Integer.parseInt(idParam);
            
            // Verifica se a tag existe e pertence ao usuário autenticado
            Tag tag = tagRepository.buscarTag(tagId);
            if (tag == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Tag não encontrada");
                ResponseUtil.sendJsonResponse(exchange, 404, response);
                return;
            }
            
            if (tag.getIdUsuario() != authenticatedUserId) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Você não tem permissão para excluir esta tag");
                ResponseUtil.sendJsonResponse(exchange, 403, response);
                return;
            }
            
            tagRepository.excluirTag(tagId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Tag excluída com sucesso");
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            ResponseUtil.sendJsonResponse(exchange, 400, response);
        }
    }
}

