package server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import server.model.*;
import server.repository.*;
import server.utils.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

public class TransactionsHandler implements HttpHandler {
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final TagRepository tagRepository;

    public TransactionsHandler() {
        this.expenseRepository = new ExpenseRepository();
        this.incomeRepository = new IncomeRepository();
        this.tagRepository = new TagRepository();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            ResponseUtil.sendErrorResponse(exchange, 405, "Método não permitido");
            return;
        }
        
        try {
            String userIdParam = RequestUtil.getQueryParam(exchange, "userId");
            String categoryIdParam = RequestUtil.getQueryParam(exchange, "categoryId");
            String dateParam = RequestUtil.getQueryParam(exchange, "date");
            
            int userId = userIdParam != null ? Integer.parseInt(userIdParam) : 1;
            Integer categoryId = (categoryIdParam != null && !categoryIdParam.isEmpty()) 
                ? Integer.parseInt(categoryIdParam) : null;
            LocalDate date = (dateParam != null && !dateParam.isEmpty()) 
                ? LocalDate.parse(dateParam) : null;
            
            List<Gasto> expenses = expenseRepository.buscarGastosComFiltros(userId, categoryId, date);
            List<Receita> incomes = (categoryId == null) 
                ? incomeRepository.buscarReceitasComFiltros(userId, date) 
                : new ArrayList<>();
            
            List<Integer> idsGastos = new ArrayList<>();
            for (Gasto gasto : expenses) {
                idsGastos.add(gasto.getIdGasto());
            }
            
            Map<Integer, List<Categoria>> categoriasPorGasto = expenseRepository.buscarCategoriasDeGastos(idsGastos);
            Map<Integer, List<Tag>> tagsPorGasto = tagRepository.buscarTagsDeGastos(idsGastos);
            Map<Integer, String[]> observacoesPorGasto = expenseRepository.buscarObservacoesDeGastos(idsGastos);
            
            List<Integer> idsReceitas = new ArrayList<>();
            for (Receita receita : incomes) {
                idsReceitas.add(receita.getIdReceita());
            }
            
            Map<Integer, List<Tag>> tagsPorReceita = tagRepository.buscarTagsDeReceitas(idsReceitas);
            Map<Integer, String[]> observacoesPorReceita = incomeRepository.buscarObservacoesDeReceitas(idsReceitas);
            
            List<Map<String, Object>> transactions = new ArrayList<>();
            
            for (Gasto gasto : expenses) {
                List<Categoria> categorias = categoriasPorGasto.getOrDefault(gasto.getIdGasto(), new ArrayList<>());
                List<String> nomesCategorias = new ArrayList<>();
                List<Integer> idsCategorias = new ArrayList<>();
                
                for (Categoria cat : categorias) {
                    nomesCategorias.add(cat.getNome());
                    idsCategorias.add(cat.getIdCategoria());
                }
                
                String categoriasStr = nomesCategorias.isEmpty() ? "Sem Categoria" : String.join(", ", nomesCategorias);
                
                List<Tag> tags = tagsPorGasto.getOrDefault(gasto.getIdGasto(), new ArrayList<>());
                List<Map<String, Object>> tagsList = new ArrayList<>();
                for (Tag tag : tags) {
                    Map<String, Object> tagMap = new HashMap<>();
                    tagMap.put("idTag", tag.getIdTag());
                    tagMap.put("nome", tag.getNome());
                    tagMap.put("cor", tag.getCor());
                    tagsList.add(tagMap);
                }
                
                String[] observacoes = observacoesPorGasto.getOrDefault(gasto.getIdGasto(), new String[0]);
                List<String> observacoesList = new ArrayList<>();
                for (String obs : observacoes) {
                    observacoesList.add(obs);
                }
                
                Map<String, Object> transaction = new HashMap<>();
                transaction.put("id", gasto.getIdGasto());
                transaction.put("type", "expense");
                transaction.put("description", gasto.getDescricao());
                transaction.put("value", gasto.getValor());
                transaction.put("date", gasto.getData().toString());
                transaction.put("category", categoriasStr);
                transaction.put("categoryIds", idsCategorias);
                transaction.put("categories", nomesCategorias);
                transaction.put("tags", tagsList);
                transaction.put("observacoes", observacoesList);
                transactions.add(transaction);
            }
            
            for (Receita receita : incomes) {
                List<Tag> tags = tagsPorReceita.getOrDefault(receita.getIdReceita(), new ArrayList<>());
                List<Map<String, Object>> tagsList = new ArrayList<>();
                for (Tag tag : tags) {
                    Map<String, Object> tagMap = new HashMap<>();
                    tagMap.put("idTag", tag.getIdTag());
                    tagMap.put("nome", tag.getNome());
                    tagMap.put("cor", tag.getCor());
                    tagsList.add(tagMap);
                }
                
                String[] observacoes = observacoesPorReceita.getOrDefault(receita.getIdReceita(), new String[0]);
                List<String> observacoesList = new ArrayList<>();
                for (String obs : observacoes) {
                    observacoesList.add(obs);
                }
                
                Map<String, Object> transaction = new HashMap<>();
                transaction.put("id", receita.getIdReceita());
                transaction.put("type", "income");
                transaction.put("description", receita.getDescricao());
                transaction.put("value", receita.getValor());
                transaction.put("date", receita.getData().toString());
                transaction.put("category", "Receita");
                transaction.put("categoryId", null);
                transaction.put("tags", tagsList);
                transaction.put("observacoes", observacoesList);
                transactions.add(transaction);
            }
            
            transactions.sort((a, b) -> ((String) b.get("date")).compareTo((String) a.get("date")));
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", transactions);
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            e.printStackTrace();
            ResponseUtil.sendErrorResponse(exchange, 500, "Erro interno do servidor");
        }
    }
}

