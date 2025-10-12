import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;

/**
 * Servidor HTTP simplificado para conectar Frontend com Backend Java
 * Implementa uma API REST básica para o sistema Controle-se
 */
public class ControleSeServer {
    private static BancoDados bancoDados;
    private static HttpServer server;
    private static final int PORT = 8080;
    
    public static void main(String[] args) {
        try {
            // Inicializa o banco de dados
            bancoDados = new BancoDados();
            
            // Cria o servidor HTTP
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            
            // Configura as rotas da API
            setupRoutes();
            
            // Inicia o servidor
            server.start();
            
            System.out.println("=== SERVIDOR CONTROLE-SE INICIADO ===");
            System.out.println("Servidor rodando em: http://localhost:" + PORT);
            System.out.println("Frontend disponível em: http://localhost:" + PORT + "/");
            System.out.println("API disponível em: http://localhost:" + PORT + "/api/");
            System.out.println("Pressione Ctrl+C para parar o servidor");
            
        } catch (IOException e) {
            System.err.println("Erro ao iniciar servidor: " + e.getMessage());
        }
    }
    
    private static void setupRoutes() {
        // Servir arquivos estáticos (HTML, CSS, JS)
        server.createContext("/", new StaticFileHandler());
        
        // API Routes básicas
        server.createContext("/api/auth/login", new LoginHandler());
        server.createContext("/api/auth/register", new RegisterHandler());
        server.createContext("/api/dashboard/overview", new OverviewHandler());
        server.createContext("/api/categories", new CategoriesHandler());
        server.createContext("/api/accounts", new AccountsHandler());
        server.createContext("/api/transactions", new TransactionsHandler());
        server.createContext("/api/expenses", new ExpensesHandler());
        server.createContext("/api/incomes", new IncomesHandler());
        server.createContext("/api/budgets", new BudgetsHandler());
        
        // Configura executor
        server.setExecutor(null);
    }
    
    // ===== HANDLERS SIMPLIFICADOS =====
    
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            
            // Serve index.html for root path
            if (path.equals("/")) {
                path = "/index.html";
            }
            
            // Try to serve file
            try {
                File file = new File("." + path);
                if (file.exists() && file.isFile()) {
                    String contentType = getContentType(path);
                    exchange.getResponseHeaders().set("Content-Type", contentType);
                    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    exchange.sendResponseHeaders(200, file.length());
                    
                    try (FileInputStream fis = new FileInputStream(file);
                         OutputStream os = exchange.getResponseBody()) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                    }
                } else {
                    // File not found
                    sendErrorResponse(exchange, 404, "Arquivo não encontrado");
                }
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Erro interno do servidor");
            }
        }
        
        private String getContentType(String path) {
            if (path.endsWith(".html")) return "text/html";
            if (path.endsWith(".css")) return "text/css";
            if (path.endsWith(".js")) return "application/javascript";
            if (path.endsWith(".json")) return "application/json";
            return "text/plain";
        }
    }
    
    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "Método não permitido");
                return;
            }
            
            try {
                String requestBody = readRequestBody(exchange);
                Map<String, String> data = parseJson(requestBody);
                
                String email = data.get("email");
                String password = data.get("password");
                
                if (bancoDados.autenticarUsuario(email, password)) {
                    Usuario usuario = bancoDados.buscarUsuarioPorEmail(email);
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("user", Map.of(
                        "id", usuario.getIdUsuario(),
                        "name", usuario.getNome(),
                        "email", usuario.getEmail(),
                        "token", "fake-token-" + usuario.getIdUsuario()
                    ));
                    
                    sendJsonResponse(exchange, 200, response);
                } else {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "Email ou senha incorretos");
                    
                    sendJsonResponse(exchange, 401, response);
                }
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Erro interno do servidor");
            }
        }
    }
    
    static class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "Método não permitido");
                return;
            }
            
            try {
                String requestBody = readRequestBody(exchange);
                Map<String, String> data = parseJson(requestBody);
                
                String name = data.get("name");
                String email = data.get("email");
                String password = data.get("password");
                
                int userId = bancoDados.cadastrarUsuario(name, email, password);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Usuário cadastrado com sucesso");
                response.put("userId", userId);
                
                sendJsonResponse(exchange, 201, response);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                
                sendJsonResponse(exchange, 400, response);
            }
        }
    }
    
    static class OverviewHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "Método não permitido");
                return;
            }
            
            try {
                // Get user ID from query parameter (in real app, from token)
                String userIdParam = getQueryParam(exchange, "userId");
                int userId = userIdParam != null ? Integer.parseInt(userIdParam) : 1;
                
                double totalIncome = bancoDados.calcularTotalReceitasUsuario(userId);
                double totalExpense = bancoDados.calcularTotalGastosUsuario(userId);
                double balance = bancoDados.calcularTotalSaldoContasUsuario(userId); // Saldo = soma de todas as contas
                double totalAccounts = balance; // Mesmo valor
                
                // Get category breakdown
                List<Categoria> categories = bancoDados.buscarCategoriasPorUsuario(userId);
                List<Map<String, Object>> categoryBreakdown = new ArrayList<>();
                
                for (Categoria categoria : categories) {
                    double categoryTotal = bancoDados.calcularTotalGastosPorCategoria(categoria.getIdCategoria());
                    Map<String, Object> categoryData = new HashMap<>();
                    categoryData.put("name", categoria.getNome());
                    categoryData.put("value", categoryTotal);
                    categoryBreakdown.add(categoryData);
                }
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", Map.of(
                    "totalIncome", totalIncome,
                    "totalExpense", totalExpense,
                    "balance", balance,
                    "totalAccounts", totalAccounts,
                    "categoryBreakdown", categoryBreakdown
                ));
                
                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Erro interno do servidor");
            }
        }
    }
    
    static class CategoriesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            
            if ("GET".equals(method)) {
                handleGetCategories(exchange);
            } else if ("POST".equals(method)) {
                handleCreateCategory(exchange);
            } else if ("PUT".equals(method)) {
                handleUpdateCategory(exchange);
            } else if ("DELETE".equals(method)) {
                handleDeleteCategory(exchange);
            } else {
                sendErrorResponse(exchange, 405, "Método não permitido");
            }
        }
        
        private void handleGetCategories(HttpExchange exchange) throws IOException {
            try {
                String userIdParam = getQueryParam(exchange, "userId");
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
                
                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Erro interno do servidor");
            }
        }
        
        private void handleCreateCategory(HttpExchange exchange) throws IOException {
            try {
                String requestBody = readRequestBody(exchange);
                Map<String, String> data = parseJson(requestBody);
                
                String name = data.get("name");
                String userIdStr = data.get("userId");
                int userId = (userIdStr != null && !userIdStr.isEmpty()) ? Integer.parseInt(userIdStr) : 1;
                
                int categoryId = bancoDados.cadastrarCategoria(name, userId);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Categoria criada com sucesso");
                response.put("categoryId", categoryId);
                
                sendJsonResponse(exchange, 201, response);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                
                sendJsonResponse(exchange, 400, response);
            }
        }
        
        private void handleUpdateCategory(HttpExchange exchange) throws IOException {
            try {
                String requestBody = readRequestBody(exchange);
                Map<String, String> data = parseJson(requestBody);
                
                int categoryId = Integer.parseInt(data.get("id"));
                String newName = data.get("name");
                
                bancoDados.atualizarCategoria(categoryId, newName);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Categoria atualizada com sucesso");
                
                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                
                sendJsonResponse(exchange, 400, response);
            }
        }
        
        private void handleDeleteCategory(HttpExchange exchange) throws IOException {
            try {
                String idParam = getQueryParam(exchange, "id");
                if (idParam == null) {
                    sendErrorResponse(exchange, 400, "ID da categoria não fornecido");
                    return;
                }
                
                int categoryId = Integer.parseInt(idParam);
                bancoDados.excluirCategoria(categoryId);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Categoria excluída com sucesso");
                
                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                
                sendJsonResponse(exchange, 400, response);
            }
        }
    }
    
    static class AccountsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            
            if ("GET".equals(method)) {
                handleGetAccounts(exchange);
            } else if ("POST".equals(method)) {
                handleCreateAccount(exchange);
            } else if ("PUT".equals(method)) {
                handleUpdateAccount(exchange);
            } else if ("DELETE".equals(method)) {
                handleDeleteAccount(exchange);
            } else {
                sendErrorResponse(exchange, 405, "Método não permitido");
            }
        }
        
        private void handleGetAccounts(HttpExchange exchange) throws IOException {
            try {
                String userIdParam = getQueryParam(exchange, "userId");
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
                
                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Erro interno do servidor");
            }
        }
        
        private void handleCreateAccount(HttpExchange exchange) throws IOException {
            try {
                String requestBody = readRequestBody(exchange);
                Map<String, String> data = parseJson(requestBody);
                
                String name = data.get("name");
                String type = data.get("type");
                double balance = Double.parseDouble(data.get("balance"));
                int userId = 1; // In real app, get from token
                
                int accountId = bancoDados.cadastrarConta(name, type, balance, userId);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Conta criada com sucesso");
                response.put("accountId", accountId);
                
                sendJsonResponse(exchange, 201, response);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                
                sendJsonResponse(exchange, 400, response);
            }
        }
        
        private void handleUpdateAccount(HttpExchange exchange) throws IOException {
            try {
                String requestBody = readRequestBody(exchange);
                Map<String, String> data = parseJson(requestBody);
                
                int accountId = Integer.parseInt(data.get("id"));
                String name = data.get("name");
                String type = data.get("type");
                double balance = Double.parseDouble(data.get("balance"));
                
                bancoDados.atualizarConta(accountId, name, type, balance);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Conta atualizada com sucesso");
                
                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                
                sendJsonResponse(exchange, 400, response);
            }
        }
        
        private void handleDeleteAccount(HttpExchange exchange) throws IOException {
            try {
                String idParam = getQueryParam(exchange, "id");
                if (idParam == null) {
                    sendErrorResponse(exchange, 400, "ID da conta não fornecido");
                    return;
                }
                
                int accountId = Integer.parseInt(idParam);
                bancoDados.excluirConta(accountId);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Conta excluída com sucesso");
                
                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                
                sendJsonResponse(exchange, 400, response);
            }
        }
    }
    
    static class TransactionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "Método não permitido");
                return;
            }
            
            try {
                // Lê parâmetros de filtro
                String userIdParam = getQueryParam(exchange, "userId");
                String categoryIdParam = getQueryParam(exchange, "categoryId");
                String dateParam = getQueryParam(exchange, "date");
                
                int userId = userIdParam != null ? Integer.parseInt(userIdParam) : 1;
                Integer categoryId = (categoryIdParam != null && !categoryIdParam.isEmpty()) 
                    ? Integer.parseInt(categoryIdParam) : null;
                LocalDate date = (dateParam != null && !dateParam.isEmpty()) 
                    ? LocalDate.parse(dateParam) : null;
                
                // Busca com filtros usando índices
                List<Gasto> expenses = bancoDados.buscarGastosComFiltros(userId, categoryId, date);
                List<Receita> incomes = bancoDados.buscarReceitasComFiltros(userId, date);
                
                List<Map<String, Object>> transactions = new ArrayList<>();
                
                // Add expenses
                for (Gasto gasto : expenses) {
                    // Busca todas as categorias do gasto (relacionamento N:N)
                    List<Categoria> categorias = bancoDados.buscarCategoriasDoGasto(gasto.getIdGasto());
                    List<String> nomesCategorias = new ArrayList<>();
                    List<Integer> idsCategorias = new ArrayList<>();
                    
                    for (Categoria cat : categorias) {
                        nomesCategorias.add(cat.getNome());
                        idsCategorias.add(cat.getIdCategoria());
                    }
                    
                    String categoriasStr = nomesCategorias.isEmpty() ? "Sem Categoria" : String.join(", ", nomesCategorias);
                    
                    Map<String, Object> transaction = new HashMap<>();
                    transaction.put("id", gasto.getIdGasto());
                    transaction.put("type", "expense");
                    transaction.put("description", gasto.getDescricao());
                    transaction.put("value", gasto.getValor());
                    transaction.put("date", gasto.getData().toString());
                    transaction.put("category", categoriasStr);
                    transaction.put("categoryIds", idsCategorias);
                    transaction.put("categories", nomesCategorias);
                    transactions.add(transaction);
                }
                
                // Add incomes (não são filtradas por categoria)
                for (Receita receita : incomes) {
                    Map<String, Object> transaction = new HashMap<>();
                    transaction.put("id", receita.getIdReceita());
                    transaction.put("type", "income");
                    transaction.put("description", receita.getDescricao());
                    transaction.put("value", receita.getValor());
                    transaction.put("date", receita.getData().toString());
                    transaction.put("category", "Receita");
                    transaction.put("categoryId", null);
                    transactions.add(transaction);
                }
                
                // Sort by date (most recent first)
                transactions.sort((a, b) -> ((String) b.get("date")).compareTo((String) a.get("date")));
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", transactions);
                
                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorResponse(exchange, 500, "Erro interno do servidor");
            }
        }
    }
    
    static class ExpensesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "Método não permitido");
                return;
            }
            
            try {
                String requestBody = readRequestBody(exchange);
                Map<String, String> data = parseJson(requestBody);
                
                String description = data.get("description");
                double value = Double.parseDouble(data.get("value"));
                LocalDate date = LocalDate.parse(data.get("date"));
                int accountId = Integer.parseInt(data.get("accountId"));
                String frequency = data.get("frequency");
                int userId = 1; // In real app, get from token
                
                // Parse categoryIds - pode ser array ou valor único
                List<Integer> categoryIds = new ArrayList<>();
                String categoryIdsStr = data.get("categoryIds");
                
                if (categoryIdsStr != null && !categoryIdsStr.isEmpty()) {
                    // Remove colchetes e espaços
                    categoryIdsStr = categoryIdsStr.replaceAll("[\\[\\]\\s]", "");
                    for (String idStr : categoryIdsStr.split(",")) {
                        if (!idStr.isEmpty()) {
                            categoryIds.add(Integer.parseInt(idStr));
                        }
                    }
                } else {
                    // Fallback para categoryId único (compatibilidade)
                    String categoryIdStr = data.get("categoryId");
                    if (categoryIdStr != null) {
                        categoryIds.add(Integer.parseInt(categoryIdStr));
                    }
                }
                
                int expenseId = bancoDados.cadastrarGasto(description, value, date, frequency, userId, categoryIds, accountId);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Gasto registrado com sucesso");
                response.put("expenseId", expenseId);
                
                sendJsonResponse(exchange, 201, response);
            } catch (Exception e) {
                e.printStackTrace();
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Erro: " + e.getMessage());
                sendJsonResponse(exchange, 400, response);
            }
        }
    }
    
    static class IncomesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "Método não permitido");
                return;
            }
            
            try {
                String requestBody = readRequestBody(exchange);
                Map<String, String> data = parseJson(requestBody);
                
                String description = data.getOrDefault("description", "Receita");
                double value = Double.parseDouble(data.get("value"));
                LocalDate date = LocalDate.parse(data.get("date"));
                int accountId = Integer.parseInt(data.get("accountId"));
                int userId = 1; // In real app, get from token
                
                int incomeId = bancoDados.cadastrarReceita(description, value, date, userId, accountId);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Receita registrada com sucesso");
                response.put("incomeId", incomeId);
                
                sendJsonResponse(exchange, 201, response);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                
                sendJsonResponse(exchange, 400, response);
            }
        }
    }
    
    static class BudgetsHandler implements HttpHandler {
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
                sendErrorResponse(exchange, 405, "Método não permitido");
                return;
            }
        }
        
        private void handleGetBudgets(HttpExchange exchange) throws IOException {
            try {
                String userIdParam = getQueryParam(exchange, "userId");
                int userId = userIdParam != null ? Integer.parseInt(userIdParam) : 1;
                
                List<Orcamento> budgets = bancoDados.buscarOrcamentosPorUsuario(userId);
                List<Map<String, Object>> budgetList = new ArrayList<>();
                
                for (Orcamento orcamento : budgets) {
                    Categoria categoria = bancoDados.buscarCategoria(orcamento.getIdCategoria());
                    double spent = bancoDados.calcularTotalGastosPorCategoria(orcamento.getIdCategoria());
                    double percentageUsed = orcamento.getValorPlanejado() > 0 ? 
                        (spent / orcamento.getValorPlanejado()) * 100 : 0;
                    
                    Map<String, Object> budgetData = new HashMap<>();
                    budgetData.put("idOrcamento", orcamento.getIdOrcamento());
                    budgetData.put("valorPlanejado", orcamento.getValorPlanejado());
                    budgetData.put("periodo", orcamento.getPeriodo());
                    budgetData.put("categoryName", categoria != null ? categoria.getNome() : "Sem categoria");
                    budgetData.put("percentageUsed", Math.min(percentageUsed, 100));
                    budgetList.add(budgetData);
                }
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", budgetList);
                
                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Erro interno do servidor");
            }
        }
        
        private void handleCreateBudget(HttpExchange exchange) throws IOException {
            try {
                String requestBody = readRequestBody(exchange);
                Map<String, String> data = parseJson(requestBody);
                
                int categoryId = Integer.parseInt(data.get("categoryId"));
                double value = Double.parseDouble(data.get("value"));
                String period = data.get("period");
                int userId = 1; // In real app, get from token
                
                int budgetId = bancoDados.cadastrarOrcamento(value, period, categoryId, userId);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Orçamento criado com sucesso");
                response.put("budgetId", budgetId);
                
                sendJsonResponse(exchange, 201, response);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                
                sendJsonResponse(exchange, 400, response);
            }
        }
        
        private void handleUpdateBudget(HttpExchange exchange) throws IOException {
            try {
                String requestBody = readRequestBody(exchange);
                Map<String, String> data = parseJson(requestBody);
                
                int budgetId = Integer.parseInt(data.get("id"));
                double value = Double.parseDouble(data.get("value"));
                String period = data.get("period");
                
                bancoDados.atualizarOrcamento(budgetId, value, period);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Orçamento atualizado com sucesso");
                
                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                
                sendJsonResponse(exchange, 400, response);
            }
        }
        
        private void handleDeleteBudget(HttpExchange exchange) throws IOException {
            try {
                String idParam = getQueryParam(exchange, "id");
                if (idParam == null) {
                    sendErrorResponse(exchange, 400, "ID do orçamento não fornecido");
                    return;
                }
                
                int budgetId = Integer.parseInt(idParam);
                bancoDados.excluirOrcamento(budgetId);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Orçamento excluído com sucesso");
                
                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                
                sendJsonResponse(exchange, 400, response);
            }
        }
    }
    
    // ===== UTILITY METHODS =====
    
    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes());
        }
    }
    
    private static Map<String, String> parseJson(String json) {
        // Robust JSON parser using regex
        Map<String, String> result = new HashMap<>();
        
        try {
            // Pattern to match "key": "value" or "key": value or "key": [array]
            // Captura strings, arrays, e valores primitivos
            Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"([^\"]*)\"|\\[([^\\]]*)\\]|([^,}\\s]+))");
            Matcher matcher = pattern.matcher(json);
            
            while (matcher.find()) {
                String key = matcher.group(1);
                String value = null;
                
                if (matcher.group(3) != null) {
                    // String value
                    value = matcher.group(3);
                } else if (matcher.group(4) != null) {
                    // Array value
                    value = "[" + matcher.group(4) + "]";
                } else if (matcher.group(5) != null) {
                    // Primitive value
                    value = matcher.group(5);
                }
                
                if (value != null) {
                    result.put(key.trim(), value.trim());
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao fazer parse do JSON: " + e.getMessage());
            System.err.println("JSON recebido: " + json);
        }
        
        return result;
    }
    
    private static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + escapeJson((String) obj) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":");
                sb.append(toJson(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escapeJson(obj.toString()) + "\"";
    }
    
    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    private static String getQueryParam(HttpExchange exchange, String paramName) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) return null;
        
        String[] params = query.split("&");
        for (String param : params) {
            String[] keyValue = param.split("=", 2); // Limit to 2 parts
            if (keyValue.length >= 1 && keyValue[0].equals(paramName)) {
                return keyValue.length == 2 ? keyValue[1] : "";
            }
        }
        return null;
    }
    
    private static void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String jsonResponse;
        if (data instanceof Map && ((Map<?, ?>) data).containsKey("success")) {
            // Data already contains full response structure
            jsonResponse = toJson(data);
        } else {
            // Wrap data in success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);
            jsonResponse = toJson(response);
        }
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        
        exchange.sendResponseHeaders(statusCode, jsonResponse.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(jsonResponse.getBytes());
        }
    }
    
    private static void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        String jsonResponse = toJson(response);
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        
        exchange.sendResponseHeaders(statusCode, jsonResponse.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(jsonResponse.getBytes());
        }
    }
}