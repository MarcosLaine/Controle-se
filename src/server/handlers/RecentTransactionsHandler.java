package server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import server.model.*;
import server.repository.*;
import server.utils.*;

import java.io.IOException;
import java.util.*;

public class RecentTransactionsHandler implements HttpHandler {
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final CategoryRepository categoryRepository;

    public RecentTransactionsHandler() {
        this.expenseRepository = new ExpenseRepository();
        this.incomeRepository = new IncomeRepository();
        this.categoryRepository = new CategoryRepository();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            ResponseUtil.sendErrorResponse(exchange, 405, "Método não permitido");
            return;
        }
        
        try {
            String userIdParam = RequestUtil.getQueryParam(exchange, "userId");
            String limitParam = RequestUtil.getQueryParam(exchange, "limit");
            
            int userId = userIdParam != null ? Integer.parseInt(userIdParam) : 1;
            int limit = limitParam != null ? Integer.parseInt(limitParam) : 10;
            
            List<Gasto> expenses = expenseRepository.buscarGastosComFiltros(userId, null, null, null, null, Integer.MAX_VALUE, 0);
            List<Receita> incomes = incomeRepository.buscarReceitasComFiltros(userId, null, null, null, Integer.MAX_VALUE, 0);
            
            List<Map<String, Object>> transactions = new ArrayList<>();
            
            for (Gasto gasto : expenses) {
                if (gasto == null || !gasto.isAtivo()) {
                    continue;
                }
                
                List<Categoria> categorias = expenseRepository.buscarCategoriasDoGasto(gasto.getIdGasto());
                List<String> nomesCategorias = new ArrayList<>();
                
                for (Categoria cat : categorias) {
                    if (cat != null && cat.isAtivo()) {
                        nomesCategorias.add(cat.getNome());
                    }
                }
                
                String categoriasStr = nomesCategorias.isEmpty() ? "Sem Categoria" : String.join(", ", nomesCategorias);
                
                Map<String, Object> transaction = new HashMap<>();
                transaction.put("id", gasto.getIdGasto());
                transaction.put("type", "expense");
                transaction.put("description", gasto.getDescricao());
                transaction.put("value", gasto.getValor());
                transaction.put("date", gasto.getData().toString());
                transaction.put("category", categoriasStr);
                transactions.add(transaction);
            }
            
            for (Receita receita : incomes) {
                if (receita == null || !receita.isAtivo()) {
                    continue;
                }
                
                Map<String, Object> transaction = new HashMap<>();
                transaction.put("id", receita.getIdReceita());
                transaction.put("type", "income");
                transaction.put("description", receita.getDescricao());
                transaction.put("value", receita.getValor());
                transaction.put("date", receita.getData().toString());
                transaction.put("category", "Receita");
                if (receita.getObservacoes() != null && receita.getObservacoes().length > 0) {
                    List<String> observacoesList = new ArrayList<>();
                    for (String obs : receita.getObservacoes()) {
                        observacoesList.add(obs);
                    }
                    transaction.put("observacoes", observacoesList);
                }
                transactions.add(transaction);
            }
            
            transactions.sort((a, b) -> ((String) b.get("date")).compareTo((String) a.get("date")));
            
            if (transactions.size() > limit) {
                transactions = transactions.subList(0, limit);
            }
            
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

