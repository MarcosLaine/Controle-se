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
            
            // Inicia o scheduler de recorrências (executa diariamente)
            iniciarSchedulerRecorrencias();
            
            // Inicia o scheduler de atualização de cotações (executa a cada 30 minutos)
            iniciarSchedulerCotacoes();
            
            // Exibe informações do servidor e inicia loop do menu
            exibirInformacoesServidor();
            iniciarLoopMenu();
            
        } catch (IOException e) {
            System.err.println("Erro ao iniciar servidor: " + e.getMessage());
        }
    }
    
    /**
     * Exibe informações do servidor
     */
    private static void exibirInformacoesServidor() {
        System.out.println("\n=== SERVIDOR CONTROLE-SE INICIADO ===");
        System.out.println("Servidor rodando em: http://localhost:" + PORT);
        System.out.println("Frontend disponível em: http://localhost:" + PORT + "/");
        System.out.println("API disponível em: http://localhost:" + PORT + "/api/");
        // System.out.println("Scheduler de recorrências: ATIVO (verifica diariamente às 00:05)");
        System.out.println("Pressione Ctrl+C para parar o servidor");
    }
    
    /**
     * Inicia loop contínuo do menu de compressão
     */
    private static void iniciarLoopMenu() {
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            try {
                exibirMenuCompressao(scanner);
            } catch (Exception e) {
                System.err.println("Erro no menu: " + e.getMessage());
                e.printStackTrace();
                System.out.println("\nPressione Enter para continuar...");
                try {
                    scanner.nextLine();
                } catch (Exception ex) {
                    // Ignora
                }
            }
        }
    }
    
    /**
     * Exibe menu CLI para compressão/descompressão de arquivos
     */
    private static void exibirMenuCompressao(Scanner scanner) {
        System.out.println("\n========================================");
        System.out.println("   SISTEMA DE COMPRESSÃO DE DADOS");
        System.out.println("========================================");
        System.out.println("1. Comprimir arquivos com LZW");
        System.out.println("2. Comprimir arquivos com Huffman");
        System.out.println("3. Descomprimir arquivo");
        System.out.println("4. Mostrar informações do servidor");
        System.out.println("========================================");
        System.out.print("Escolha uma opção: ");
        
        try {
            int opcao = scanner.nextInt();
            scanner.nextLine(); // Limpa buffer
            
            switch (opcao) {
                case 1:
                    comprimirArquivos("LZW", scanner);
                    break;
                case 2:
                    comprimirArquivos("HUFFMAN", scanner);
                    break;
                case 3:
                    descomprimirArquivo(scanner);
                    break;
                case 4:
                    exibirInformacoesServidor();
                    System.out.println("\nPressione Enter para continuar...");
                    scanner.nextLine();
                    break;
                default:
                    System.out.println("Opção inválida. Tente novamente.");
                    System.out.println("Pressione Enter para continuar...");
                    scanner.nextLine();
                    break;
            }
        } catch (Exception e) {
            System.err.println("Erro ao processar opção: " + e.getMessage());
            System.out.println("Pressione Enter para continuar...");
            try {
                scanner.nextLine();
            } catch (Exception ex) {
                // Ignora
            }
        }
    }
    
    /**
     * Comprime todos os arquivos .db usando o algoritmo especificado
     */
    private static void comprimirArquivos(String algoritmo, Scanner scanner) {
        try {
            System.out.println("\n=== COMPRESSÃO COM " + algoritmo + " ===\n");
            String arquivoComprimido = FileCompressor.compressAllFiles(algoritmo);
            System.out.println("\nCompressão concluída com sucesso!");
            System.out.println("Arquivo salvo em: " + arquivoComprimido);
            System.out.println("\nPressione Enter para continuar...");
            scanner.nextLine();
            exibirInformacoesServidor();
        } catch (Exception e) {
            System.err.println("\nErro ao comprimir arquivos: " + e.getMessage());
            e.printStackTrace();
            System.out.println("\nPressione Enter para continuar...");
            try {
                scanner.nextLine();
            } catch (Exception ex) {
                // Ignora
            }
            exibirInformacoesServidor();
        }
    }
    
    /**
     * Descomprime um arquivo selecionado
     */
    private static void descomprimirArquivo(Scanner scanner) {
        try {
            System.out.println("\n=== DESCOMPRESSÃO DE ARQUIVO ===\n");
            
            List<String> arquivosComprimidos = FileCompressor.listCompressedFiles();
            
            if (arquivosComprimidos.isEmpty()) {
                System.out.println("Nenhum arquivo comprimido encontrado no diretório atual.");
                System.out.println("Pressione Enter para continuar...");
                scanner.nextLine();
                exibirInformacoesServidor();
                return;
            }
            
            System.out.println("Arquivos comprimidos disponíveis:");
            for (int i = 0; i < arquivosComprimidos.size(); i++) {
                File file = new File(arquivosComprimidos.get(i));
                System.out.println((i + 1) + ". " + file.getName() + " (" + file.length() + " bytes)");
            }
            
            System.out.print("\nEscolha o número do arquivo para descomprimir (ou 0 para cancelar): ");
            int escolha = scanner.nextInt();
            scanner.nextLine(); // Limpa buffer
            
            if (escolha < 1 || escolha > arquivosComprimidos.size()) {
                System.out.println("Opção cancelada ou inválida.");
                exibirInformacoesServidor();
                return;
            }
            
            String arquivoSelecionado = arquivosComprimidos.get(escolha - 1);
            System.out.println("\nDescomprimindo: " + new File(arquivoSelecionado).getName());
            
            FileCompressor.decompressAllFiles(arquivoSelecionado);
            
            System.out.println("\nDescompressão concluída com sucesso!");
            System.out.println("Pressione Enter para continuar...");
            scanner.nextLine();
            exibirInformacoesServidor();
            
        } catch (Exception e) {
            System.err.println("\nErro ao descomprimir arquivo: " + e.getMessage());
            e.printStackTrace();
            System.out.println("\nPressione Enter para continuar...");
            try {
                scanner.nextLine();
            } catch (Exception ex) {
                // Ignora
            }
            exibirInformacoesServidor();
        }
    }
    
    /**
     * Inicia o scheduler que processa recorrências automáticas
     * Executa diariamente às 00:05 da manhã
     */
    private static void iniciarSchedulerRecorrencias() {
        Timer timer = new Timer("RecorrenciasScheduler", true); // daemon=true para não bloquear shutdown
        
        // Calcula o delay até a próxima execução (hoje às 00:05 ou amanhã às 00:05)
        Calendar proximaExecucao = Calendar.getInstance();
        proximaExecucao.set(Calendar.HOUR_OF_DAY, 0);
        proximaExecucao.set(Calendar.MINUTE, 5);
        proximaExecucao.set(Calendar.SECOND, 0);
        proximaExecucao.set(Calendar.MILLISECOND, 0);
        
        // Se já passou da hora hoje, agenda para amanhã
        if (proximaExecucao.getTimeInMillis() < System.currentTimeMillis()) {
            proximaExecucao.add(Calendar.DAY_OF_MONTH, 1);
        }
        
        long delay = proximaExecucao.getTimeInMillis() - System.currentTimeMillis();
        long periodo = 24 * 60 * 60 * 1000; // 24 horas em milissegundos
        
        // Agenda a tarefa para executar diariamente
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    System.out.println("\n=== SCHEDULER DE RECORRÊNCIAS ===");
                    System.out.println("Executando processamento automático...");
                    int criados = bancoDados.processarRecorrencias();
                    System.out.println("Total de transações recorrentes criadas: " + criados);
                    System.out.println("=====================================\n");
                } catch (Exception e) {
                    System.err.println("Erro ao processar recorrências: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, delay, periodo);
        
        // Para fins de DEBUG/TESTE: executa imediatamente na inicialização também
        System.out.println("\n[INICIALIZAÇÃO] Processando recorrências pendentes...");
        try {
            int criados = bancoDados.processarRecorrencias();
            if (criados > 0) {
                System.out.println("[INICIALIZAÇÃO] " + criados + " transações recorrentes criadas");
            } else {
                System.out.println("[INICIALIZAÇÃO] Nenhuma recorrência pendente");
            }
        } catch (Exception e) {
            System.err.println("[INICIALIZAÇÃO] Erro ao processar recorrências: " + e.getMessage());
        }
        System.out.println();
    }
    
    /**
     * Inicia o scheduler que atualiza cotações de investimentos
     * Executa a cada 30 minutos
     */
    private static void iniciarSchedulerCotacoes() {
        Timer timer = new Timer("CotacoesScheduler", true); // daemon=true para não bloquear shutdown
        
        // Executa imediatamente e depois a cada 30 minutos
        long periodo = 30 * 60 * 1000; // 30 minutos em milissegundos
        
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    System.out.println("\n=== ATUALIZAÇÃO DE COTAÇÕES ===");
                    QuoteService quoteService = QuoteService.getInstance();
                    quoteService.cleanExpiredCache();
                    System.out.println("Cache de cotações limpo e atualizado");
                    System.out.println("=====================================\n");
                } catch (Exception e) {
                    System.err.println("Erro ao atualizar cotações: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, 0, periodo);
        
        System.out.println("[INICIALIZAÇÃO] Scheduler de cotações iniciado (atualiza a cada 30 minutos)");
    }
    
    private static void setupRoutes() {
        // API Routes básicas (devem vir antes do StaticFileHandler)
        server.createContext("/api/auth/login", new LoginHandler());
        server.createContext("/api/auth/register", new RegisterHandler());
        server.createContext("/api/dashboard/overview", new OverviewHandler());
        server.createContext("/api/categories", new CategoriesHandler());
        server.createContext("/api/accounts", new AccountsHandler());
        server.createContext("/api/transactions/recent", new RecentTransactionsHandler());
        server.createContext("/api/transactions", new TransactionsHandler());
        server.createContext("/api/expenses", new ExpensesHandler());
        server.createContext("/api/incomes", new IncomesHandler());
        server.createContext("/api/budgets", new BudgetsHandler());
        server.createContext("/api/tags", new TagsHandler());
        server.createContext("/api/reports", new ReportsHandler());
        server.createContext("/api/investments", new InvestmentsHandler());
        server.createContext("/api/investments/quote", new InvestmentQuoteHandler());
        
        // Servir arquivos estáticos (HTML, CSS, JS) - deve vir por último
        server.createContext("/", new StaticFileHandler());
        
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
                double balance = bancoDados.calcularSaldoContasUsuarioSemInvestimento(userId); // Saldo sem investimentos
                double netWorth = bancoDados.calcularTotalSaldoContasUsuario(userId); // Patrimônio total
                
                // Get category breakdown
                List<Categoria> categories = bancoDados.buscarCategoriasPorUsuario(userId);
                List<Map<String, Object>> categoryBreakdown = new ArrayList<>();
                
                for (Categoria categoria : categories) {
                    // Calcula apenas os gastos do usuário para esta categoria
                    double categoryTotal = bancoDados.calcularTotalGastosPorCategoriaEUsuario(categoria.getIdCategoria(), userId);
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
                    "netWorth", netWorth,
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
                Map<String, Object> data = parseJsonWithNested(requestBody);
                
                String name = (String) data.get("name");
                Object userIdObj = data.get("userId");
                int userId = 1;
                if (userIdObj instanceof Number) {
                    userId = ((Number) userIdObj).intValue();
                } else if (userIdObj instanceof String) {
                    userId = Integer.parseInt((String) userIdObj);
                }
                
                int categoryId = bancoDados.cadastrarCategoria(name, userId);
                
                // Verifica se deve criar orçamento junto
                Integer budgetId = null;
                if (data.containsKey("budget") && data.get("budget") != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> budgetData = (Map<String, Object>) data.get("budget");
                    
                    double value = 0.0;
                    Object valueObj = budgetData.get("value");
                    if (valueObj instanceof Number) {
                        value = ((Number) valueObj).doubleValue();
                    } else if (valueObj instanceof String) {
                        value = Double.parseDouble((String) valueObj);
                    }
                    
                    String period = (String) budgetData.get("period");
                    
                    if (value > 0 && period != null && !period.isEmpty()) {
                        budgetId = bancoDados.cadastrarOrcamento(value, period, categoryId, userId);
                    }
                }
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                if (budgetId != null) {
                    response.put("message", "Categoria e orçamento criados com sucesso");
                    response.put("budgetId", budgetId);
                } else {
                    response.put("message", "Categoria criada com sucesso");
                }
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
                
                // Obtém userId do request
                String userIdStr = data.get("userId");
                int userId = 1; // Default para compatibilidade
                if (userIdStr != null && !userIdStr.isEmpty()) {
                    userId = Integer.parseInt(userIdStr);
                }
                
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
    
    static class RecentTransactionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "Método não permitido");
                return;
            }
            
            try {
                // Parâmetros opcionais
                String userIdParam = getQueryParam(exchange, "userId");
                String limitParam = getQueryParam(exchange, "limit");
                
                int userId = userIdParam != null ? Integer.parseInt(userIdParam) : 1;
                int limit = limitParam != null ? Integer.parseInt(limitParam) : 10;
                
                // Busca todas as transações do usuário
                List<Gasto> expenses = bancoDados.buscarGastosComFiltros(userId, null, null);
                List<Receita> incomes = bancoDados.buscarReceitasComFiltros(userId, null);
                
                List<Map<String, Object>> transactions = new ArrayList<>();
                
                // Add expenses
                for (Gasto gasto : expenses) {
                    // Verifica se o gasto está ativo
                    if (gasto == null || !gasto.isAtivo()) {
                        continue;
                    }
                    
                    // Busca todas as categorias do gasto
                    List<Categoria> categorias = bancoDados.buscarCategoriasDoGasto(gasto.getIdGasto());
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
                
                // Add incomes
                for (Receita receita : incomes) {
                    // Verifica se a receita está ativa
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
                    transactions.add(transaction);
                }
                
                // Sort by date (most recent first)
                transactions.sort((a, b) -> ((String) b.get("date")).compareTo((String) a.get("date")));
                
                // Limit to recent transactions
                if (transactions.size() > limit) {
                    transactions = transactions.subList(0, limit);
                }
                
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
                    
                    // Busca tags do gasto
                    List<Tag> tags = bancoDados.buscarTagsGasto(gasto.getIdGasto());
                    List<Map<String, Object>> tagsList = new ArrayList<>();
                    for (Tag tag : tags) {
                        Map<String, Object> tagMap = new HashMap<>();
                        tagMap.put("idTag", tag.getIdTag());
                        tagMap.put("nome", tag.getNome());
                        tagMap.put("cor", tag.getCor());
                        tagsList.add(tagMap);
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
                    // Converte array de observações para lista
                    List<String> observacoesList = new ArrayList<>();
                    if (gasto.getObservacoes() != null) {
                        for (String obs : gasto.getObservacoes()) {
                            observacoesList.add(obs);
                        }
                    }
                    transaction.put("observacoes", observacoesList);
                    transactions.add(transaction);
                }
                
                // Add incomes (não são filtradas por categoria)
                for (Receita receita : incomes) {
                    // Busca tags da receita
                    List<Tag> tags = bancoDados.buscarTagsReceita(receita.getIdReceita());
                    List<Map<String, Object>> tagsList = new ArrayList<>();
                    for (Tag tag : tags) {
                        Map<String, Object> tagMap = new HashMap<>();
                        tagMap.put("idTag", tag.getIdTag());
                        tagMap.put("nome", tag.getNome());
                        tagMap.put("cor", tag.getCor());
                        tagsList.add(tagMap);
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
                Map<String, Object> data = parseJsonWithNested(requestBody);
                
                String description = (String) data.get("description");
                double value = ((Number) data.get("value")).doubleValue();
                LocalDate date = LocalDate.parse((String) data.get("date"));
                int accountId = ((Number) data.get("accountId")).intValue();
                String frequency = (String) data.get("frequency");
                
                // Obtém userId do request
                Object userIdObj = data.get("userId");
                int userId = 1; // Default para compatibilidade
                if (userIdObj != null) {
                    if (userIdObj instanceof Number) {
                        userId = ((Number) userIdObj).intValue();
                    } else if (userIdObj instanceof String) {
                        userId = Integer.parseInt((String) userIdObj);
                    }
                }
                
                // Parse categoryIds - pode ser array ou valor único
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
                        // Fallback: parse string como array
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
                    // Fallback para categoryId único (compatibilidade)
                    Object categoryIdObj = data.get("categoryId");
                    if (categoryIdObj instanceof Number) {
                        categoryIds.add(((Number) categoryIdObj).intValue());
                    } else if (categoryIdObj instanceof String) {
                        categoryIds.add(Integer.parseInt((String) categoryIdObj));
                    }
                }
                
                // Parse tagIds (opcional) - pode ser array
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
                        // Fallback: parse string como array
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
                
                // Parse observações (opcional)
                String[] observacoes = null;
                Object observacoesObj = data.get("observacoes");
                if (observacoesObj != null) {
                    if (observacoesObj instanceof String) {
                        String observacoesStr = (String) observacoesObj;
                        if (!observacoesStr.trim().isEmpty()) {
                            // Divide por quebras de linha ou vírgulas
                            String[] obsArray = observacoesStr.split("[\\n,;]");
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
                
                int expenseId = bancoDados.cadastrarGasto(description, value, date, frequency, userId, categoryIds, accountId, observacoes);
                
                // Associa tags se houver
                for (int tagId : tagIds) {
                    bancoDados.associarTagTransacao(expenseId, "GASTO", tagId);
                }
                
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
                Map<String, Object> data = parseJsonWithNested(requestBody);
                
                String description = (String) data.getOrDefault("description", "Receita");
                double value = ((Number) data.get("value")).doubleValue();
                LocalDate date = LocalDate.parse((String) data.get("date"));
                int accountId = ((Number) data.get("accountId")).intValue();
                
                // Obtém userId do request
                Object userIdObj = data.get("userId");
                int userId = 1; // Default para compatibilidade
                if (userIdObj != null) {
                    if (userIdObj instanceof Number) {
                        userId = ((Number) userIdObj).intValue();
                    } else if (userIdObj instanceof String) {
                        userId = Integer.parseInt((String) userIdObj);
                    }
                }
                
                // Parse tagIds (opcional) - pode ser array
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
                        // Fallback: parse string como array
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
                
                int incomeId = bancoDados.cadastrarReceita(description, value, date, userId, accountId);
                
                // Associa tags se houver
                for (int tagId : tagIds) {
                    bancoDados.associarTagTransacao(incomeId, "RECEITA", tagId);
                }
                
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
                    // Calcula apenas os gastos do usuário para esta categoria
                    double spent = bancoDados.calcularTotalGastosPorCategoriaEUsuario(orcamento.getIdCategoria(), userId);
                    double percentageUsed = orcamento.getValorPlanejado() > 0 ? 
                        (spent / orcamento.getValorPlanejado()) * 100 : 0;
                    
                    // Formata para 2 casas decimais
                    percentageUsed = Math.min(percentageUsed, 100);
                    percentageUsed = Math.round(percentageUsed * 100.0) / 100.0;
                    
                    Map<String, Object> budgetData = new HashMap<>();
                    budgetData.put("idOrcamento", orcamento.getIdOrcamento());
                    budgetData.put("valorPlanejado", orcamento.getValorPlanejado());
                    budgetData.put("periodo", orcamento.getPeriodo());
                    budgetData.put("categoryName", categoria != null ? categoria.getNome() : "Sem categoria");
                    budgetData.put("percentageUsed", percentageUsed);
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
                
                // Obtém userId do request
                String userIdStr = data.get("userId");
                int userId = 1; // Default para compatibilidade
                if (userIdStr != null && !userIdStr.isEmpty()) {
                    userId = Integer.parseInt(userIdStr);
                }
                
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
    
    static class TagsHandler implements HttpHandler {
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
                sendErrorResponse(exchange, 405, "Método não permitido");
            }
        }
        
        private void handleGetTags(HttpExchange exchange) throws IOException {
            try {
                String userIdParam = getQueryParam(exchange, "userId");
                int userId = userIdParam != null ? Integer.parseInt(userIdParam) : 1;
                
                List<Tag> tags = bancoDados.buscarTagsPorUsuario(userId);
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
                
                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Erro interno do servidor");
            }
        }
        
        private void handleCreateTag(HttpExchange exchange) throws IOException {
            try {
                String requestBody = readRequestBody(exchange);
                Map<String, String> data = parseJson(requestBody);
                
                String nome = data.get("nome");
                String cor = data.get("cor");
                if (cor == null || cor.isEmpty()) {
                    cor = "#6B7280"; // Cor padrão cinza
                }
                String userIdStr = data.get("userId");
                int userId = (userIdStr != null && !userIdStr.isEmpty()) ? Integer.parseInt(userIdStr) : 1;
                
                int tagId = bancoDados.cadastrarTag(nome, cor, userId);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Tag criada com sucesso");
                response.put("tagId", tagId);
                
                sendJsonResponse(exchange, 201, response);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                
                sendJsonResponse(exchange, 400, response);
            }
        }
        
        private void handleUpdateTag(HttpExchange exchange) throws IOException {
            try {
                String requestBody = readRequestBody(exchange);
                Map<String, String> data = parseJson(requestBody);
                
                int tagId = Integer.parseInt(data.get("id"));
                String nome = data.get("nome");
                String cor = data.get("cor");
                
                bancoDados.atualizarTag(tagId, nome, cor);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Tag atualizada com sucesso");
                
                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                
                sendJsonResponse(exchange, 400, response);
            }
        }
        
        private void handleDeleteTag(HttpExchange exchange) throws IOException {
            try {
                String idParam = getQueryParam(exchange, "id");
                if (idParam == null) {
                    sendErrorResponse(exchange, 400, "ID da tag não fornecido");
                    return;
                }
                
                int tagId = Integer.parseInt(idParam);
                bancoDados.excluirTag(tagId);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Tag excluída com sucesso");
                
                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                
                sendJsonResponse(exchange, 400, response);
            }
        }
    }
    
    static class ReportsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            
            if ("GET".equals(method)) {
                handleGetReports(exchange);
            } else if ("POST".equals(method)) {
                handleExportReport(exchange);
            } else {
                sendErrorResponse(exchange, 405, "Método não permitido");
            }
        }
        
        private void handleGetReports(HttpExchange exchange) throws IOException {
            try {
                String userIdParam = getQueryParam(exchange, "userId");
                String periodParam = getQueryParam(exchange, "period");
                String startDateParam = getQueryParam(exchange, "startDate");
                String endDateParam = getQueryParam(exchange, "endDate");
                
                int userId = userIdParam != null ? Integer.parseInt(userIdParam) : 1;
                String period = periodParam != null ? periodParam : "month";
                
                // Calcula datas baseado no período
                LocalDate startDate, endDate;
                LocalDate now = LocalDate.now();
                
                if (startDateParam != null && endDateParam != null) {
                    startDate = LocalDate.parse(startDateParam);
                    endDate = LocalDate.parse(endDateParam);
                } else if ("year".equals(period)) {
                    startDate = now.withDayOfYear(1);
                    endDate = now.withDayOfYear(now.lengthOfYear());
                } else { // month
                    startDate = now.withDayOfMonth(1);
                    endDate = now.withDayOfMonth(now.lengthOfMonth());
                }
                
                // Busca dados para o relatório
                List<Gasto> expenses = bancoDados.buscarGastosPorPeriodo(userId, startDate, endDate);
                List<Receita> incomes = bancoDados.buscarReceitasPorPeriodo(userId, startDate, endDate);
                
                // Calcula totais
                double totalExpenses = expenses.stream().mapToDouble(Gasto::getValor).sum();
                double totalIncomes = incomes.stream().mapToDouble(Receita::getValor).sum();
                double balance = totalIncomes - totalExpenses;
                
                // Análise por categoria
                Map<String, Double> categoryAnalysis = new HashMap<>();
                for (Gasto gasto : expenses) {
                    List<Categoria> categorias = bancoDados.buscarCategoriasDoGasto(gasto.getIdGasto());
                    for (Categoria categoria : categorias) {
                        categoryAnalysis.merge(categoria.getNome(), gasto.getValor(), Double::sum);
                    }
                }
                
                // Análise por conta
                Map<String, Double> accountAnalysis = new HashMap<>();
                for (Gasto gasto : expenses) {
                    Conta conta = bancoDados.buscarConta(gasto.getIdConta());
                    if (conta != null) {
                        accountAnalysis.merge(conta.getNome(), gasto.getValor(), Double::sum);
                    }
                }
                
                // Análise mensal (últimos 12 meses)
                List<Map<String, Object>> monthlyAnalysis = new ArrayList<>();
                for (int i = 11; i >= 0; i--) {
                    LocalDate monthStart = now.minusMonths(i).withDayOfMonth(1);
                    LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
                    
                    List<Gasto> monthExpenses = bancoDados.buscarGastosPorPeriodo(userId, monthStart, monthEnd);
                    List<Receita> monthIncomes = bancoDados.buscarReceitasPorPeriodo(userId, monthStart, monthEnd);
                    
                    double monthExpenseTotal = monthExpenses.stream().mapToDouble(Gasto::getValor).sum();
                    double monthIncomeTotal = monthIncomes.stream().mapToDouble(Receita::getValor).sum();
                    
                    Map<String, Object> monthData = new HashMap<>();
                    monthData.put("month", monthStart.getMonthValue());
                    monthData.put("year", monthStart.getYear());
                    monthData.put("monthName", monthStart.getMonth().name());
                    monthData.put("expenses", monthExpenseTotal);
                    monthData.put("incomes", monthIncomeTotal);
                    monthData.put("balance", monthIncomeTotal - monthExpenseTotal);
                    monthlyAnalysis.add(monthData);
                }
                
                // Top 5 gastos
                List<Map<String, Object>> topExpenses = new ArrayList<>();
                expenses.stream()
                    .sorted((a, b) -> Double.compare(b.getValor(), a.getValor()))
                    .limit(5)
                    .forEach(gasto -> {
                        Map<String, Object> expenseData = new HashMap<>();
                        expenseData.put("description", gasto.getDescricao());
                        expenseData.put("value", gasto.getValor());
                        expenseData.put("date", gasto.getData().toString());
                        
                        List<Categoria> categorias = bancoDados.buscarCategoriasDoGasto(gasto.getIdGasto());
                        String categoryNames = categorias.stream()
                            .map(Categoria::getNome)
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("Sem categoria");
                        expenseData.put("category", categoryNames);
                        
                        topExpenses.add(expenseData);
                    });
                
                // Prepara resposta
                Map<String, Object> reportData = new HashMap<>();
                reportData.put("period", period);
                reportData.put("startDate", startDate.toString());
                reportData.put("endDate", endDate.toString());
                reportData.put("totalExpenses", totalExpenses);
                reportData.put("totalIncomes", totalIncomes);
                reportData.put("balance", balance);
                reportData.put("categoryAnalysis", categoryAnalysis);
                reportData.put("accountAnalysis", accountAnalysis);
                reportData.put("monthlyAnalysis", monthlyAnalysis);
                reportData.put("topExpenses", topExpenses);
                reportData.put("expenseCount", expenses.size());
                reportData.put("incomeCount", incomes.size());
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", reportData);
                
                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorResponse(exchange, 500, "Erro interno do servidor");
            }
        }
        
        private void handleExportReport(HttpExchange exchange) throws IOException {
            try {
                String requestBody = readRequestBody(exchange);
                Map<String, String> data = parseJson(requestBody);
                
                String userIdParam = data.get("userId");
                String format = data.get("format"); // "csv" ou "xlsx"
                String period = data.get("period");
                String startDateParam = data.get("startDate");
                String endDateParam = data.get("endDate");
                
                int userId = userIdParam != null ? Integer.parseInt(userIdParam) : 1;
                
                // Calcula datas
                LocalDate startDate, endDate;
                LocalDate now = LocalDate.now();
                
                if (startDateParam != null && endDateParam != null) {
                    startDate = LocalDate.parse(startDateParam);
                    endDate = LocalDate.parse(endDateParam);
                } else if ("year".equals(period)) {
                    startDate = now.withDayOfYear(1);
                    endDate = now.withDayOfYear(now.lengthOfYear());
                } else { // month
                    startDate = now.withDayOfMonth(1);
                    endDate = now.withDayOfMonth(now.lengthOfMonth());
                }
                
                // Busca dados
                List<Gasto> expenses = bancoDados.buscarGastosPorPeriodo(userId, startDate, endDate);
                List<Receita> incomes = bancoDados.buscarReceitasPorPeriodo(userId, startDate, endDate);
                
                if ("csv".equals(format)) {
                    exportToCSV(exchange, expenses, incomes, startDate, endDate);
                } else if ("xlsx".equals(format)) {
                    exportToXLSX(exchange, expenses, incomes, startDate, endDate);
                } else {
                    sendErrorResponse(exchange, 400, "Formato não suportado. Use 'csv' ou 'xlsx'");
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorResponse(exchange, 500, "Erro interno do servidor");
            }
        }
        
        private void exportToCSV(HttpExchange exchange, List<Gasto> expenses, List<Receita> incomes, 
                                LocalDate startDate, LocalDate endDate) throws IOException {
            StringBuilder csv = new StringBuilder();
            
            // Header
            csv.append("Tipo,Descrição,Valor,Data,Categoria,Conta,Observações\n");
            
            // Expenses
            for (Gasto gasto : expenses) {
                List<Categoria> categorias = bancoDados.buscarCategoriasDoGasto(gasto.getIdGasto());
                String categoryNames = categorias.stream()
                    .map(Categoria::getNome)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("Sem categoria");
                
                Conta conta = bancoDados.buscarConta(gasto.getIdConta());
                String accountName = conta != null ? conta.getNome() : "Conta não encontrada";
                
                String observacoes = "";
                if (gasto.getObservacoes() != null && gasto.getObservacoes().length > 0) {
                    observacoes = String.join("; ", gasto.getObservacoes());
                }
                
                csv.append("Gasto,")
                   .append(escapeCsv(gasto.getDescricao())).append(",")
                   .append(gasto.getValor()).append(",")
                   .append(gasto.getData()).append(",")
                   .append(escapeCsv(categoryNames)).append(",")
                   .append(escapeCsv(accountName)).append(",")
                   .append(escapeCsv(observacoes)).append("\n");
            }
            
            // Incomes
            for (Receita receita : incomes) {
                Conta conta = bancoDados.buscarConta(receita.getIdConta());
                String accountName = conta != null ? conta.getNome() : "Conta não encontrada";
                
                csv.append("Receita,")
                   .append(escapeCsv(receita.getDescricao())).append(",")
                   .append(receita.getValor()).append(",")
                   .append(receita.getData()).append(",")
                   .append("Receita,")
                   .append(escapeCsv(accountName)).append(",")
                   .append("").append("\n");
            }
            
            String filename = "relatorio_" + startDate + "_" + endDate + ".csv";
            
            exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            
            byte[] csvBytes = csv.toString().getBytes("UTF-8");
            exchange.sendResponseHeaders(200, csvBytes.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(csvBytes);
            }
        }
        
        private void exportToXLSX(HttpExchange exchange, List<Gasto> expenses, List<Receita> incomes, 
                                 LocalDate startDate, LocalDate endDate) throws IOException {
            // Para XLSX, vamos retornar um CSV com extensão .xlsx por simplicidade
            // Em uma implementação real, usaria Apache POI
            StringBuilder xlsx = new StringBuilder();
            
            // Header
            xlsx.append("Tipo\tDescrição\tValor\tData\tCategoria\tConta\tObservações\n");
            
            // Expenses
            for (Gasto gasto : expenses) {
                List<Categoria> categorias = bancoDados.buscarCategoriasDoGasto(gasto.getIdGasto());
                String categoryNames = categorias.stream()
                    .map(Categoria::getNome)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("Sem categoria");
                
                Conta conta = bancoDados.buscarConta(gasto.getIdConta());
                String accountName = conta != null ? conta.getNome() : "Conta não encontrada";
                
                String observacoes = "";
                if (gasto.getObservacoes() != null && gasto.getObservacoes().length > 0) {
                    observacoes = String.join("; ", gasto.getObservacoes());
                }
                
                xlsx.append("Gasto\t")
                   .append(gasto.getDescricao()).append("\t")
                   .append(gasto.getValor()).append("\t")
                   .append(gasto.getData()).append("\t")
                   .append(categoryNames).append("\t")
                   .append(accountName).append("\t")
                   .append(observacoes).append("\n");
            }
            
            // Incomes
            for (Receita receita : incomes) {
                Conta conta = bancoDados.buscarConta(receita.getIdConta());
                String accountName = conta != null ? conta.getNome() : "Conta não encontrada";
                
                xlsx.append("Receita\t")
                   .append(receita.getDescricao()).append("\t")
                   .append(receita.getValor()).append("\t")
                   .append(receita.getData()).append("\t")
                   .append("Receita\t")
                   .append(accountName).append("\t")
                   .append("").append("\n");
            }
            
            String filename = "relatorio_" + startDate + "_" + endDate + ".xlsx";
            
            exchange.getResponseHeaders().set("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            
            byte[] xlsxBytes = xlsx.toString().getBytes("UTF-8");
            exchange.sendResponseHeaders(200, xlsxBytes.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(xlsxBytes);
            }
        }
        
        private String escapeCsv(String value) {
            if (value == null) return "";
            if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                return "\"" + value.replace("\"", "\"\"") + "\"";
            }
            return value;
        }
    }
    
    static class InvestmentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            
            if ("GET".equals(method)) {
                handleGetInvestments(exchange);
            } else if ("POST".equals(method)) {
                handleCreateInvestment(exchange);
            } else if ("PUT".equals(method)) {
                handleUpdateInvestment(exchange);
            } else if ("DELETE".equals(method)) {
                handleDeleteInvestment(exchange);
            } else {
                sendErrorResponse(exchange, 405, "Método não permitido");
            }
        }
        
        private void handleGetInvestments(HttpExchange exchange) throws IOException {
            try {
                String userIdParam = getQueryParam(exchange, "userId");
                int userId = userIdParam != null ? Integer.parseInt(userIdParam) : 1;
                
                List<Investimento> investments = bancoDados.buscarInvestimentosPorUsuario(userId);
                QuoteService quoteService = QuoteService.getInstance();
                
                List<Map<String, Object>> investmentList = new ArrayList<>();
                double totalInvested = 0;
                double totalCurrent = 0;
                
                for (Investimento inv : investments) {
                    // Busca cotação atual
                    QuoteService.QuoteResult quote = quoteService.getQuote(inv.getNome(), inv.getCategoria(), null);
                    double currentPrice = quote != null && quote.success ? quote.price : inv.getPrecoAporte();
                    
                    // Converte para BRL se necessário
                    if (quote != null && !"BRL".equals(inv.getMoeda()) && !"BRL".equals(quote.currency)) {
                        double exchangeRate = quoteService.getExchangeRate(quote.currency, "BRL");
                        currentPrice *= exchangeRate;
                    }
                    
                    double currentValue = inv.getQuantidade() * currentPrice;
                    double returnValue = currentValue - inv.getValorAporte();
                    double returnPercent = inv.getValorAporte() > 0 ? (returnValue / inv.getValorAporte()) * 100 : 0;
                    
                    totalInvested += inv.getValorAporte();
                    totalCurrent += currentValue;
                    
                    Map<String, Object> invData = new HashMap<>();
                    invData.put("idInvestimento", inv.getIdInvestimento());
                    invData.put("nome", inv.getNome());
                    invData.put("nomeAtivo", inv.getNomeAtivo());
                    invData.put("categoria", inv.getCategoria());
                    invData.put("quantidade", inv.getQuantidade());
                    invData.put("precoAporte", inv.getPrecoAporte());
                    invData.put("valorAporte", inv.getValorAporte());
                    invData.put("corretagem", inv.getCorretagem());
                    invData.put("corretora", inv.getCorretora());
                    invData.put("dataAporte", inv.getDataAporte().toString());
                    invData.put("moeda", inv.getMoeda());
                    invData.put("precoAtual", currentPrice);
                    invData.put("valorAtual", currentValue);
                    invData.put("retorno", returnValue);
                    invData.put("retornoPercent", returnPercent);
                    investmentList.add(invData);
                }
                
                Map<String, Object> summary = new HashMap<>();
                summary.put("totalInvested", totalInvested);
                summary.put("totalCurrent", totalCurrent);
                summary.put("totalReturn", totalCurrent - totalInvested);
                summary.put("totalReturnPercent", totalInvested > 0 ? ((totalCurrent - totalInvested) / totalInvested) * 100 : 0);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", investmentList);
                response.put("summary", summary);
                
                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorResponse(exchange, 500, "Erro interno do servidor: " + e.getMessage());
            }
        }
        
        private void handleCreateInvestment(HttpExchange exchange) throws IOException {
            try {
                String requestBody = readRequestBody(exchange);
                Map<String, Object> data = parseJsonWithNested(requestBody);
                
                String nome = (String) data.get("nome");
                String categoria = (String) data.get("categoria");
                double quantidade = ((Number) data.get("quantidade")).doubleValue();
                double corretagem = data.containsKey("corretagem") ? 
                    ((Number) data.get("corretagem")).doubleValue() : 0.0;
                
                // Valida quantidade mínima
                if (quantidade <= 0) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "A quantidade deve ser maior que zero");
                    sendJsonResponse(exchange, 400, response);
                    return;
                }
                String dataAporteStr = (String) data.get("dataAporte");
                LocalDate dataAporte = dataAporteStr != null ? LocalDate.parse(dataAporteStr) : LocalDate.now();
                
                Object userIdObj = data.get("userId");
                int userId = 1;
                if (userIdObj instanceof Number) {
                    userId = ((Number) userIdObj).intValue();
                } else if (userIdObj instanceof String) {
                    userId = Integer.parseInt((String) userIdObj);
                }
                
                int accountId = ((Number) data.get("accountId")).intValue();
                String moeda = (String) data.getOrDefault("moeda", "BRL");
                
                // Valida se a conta é do tipo "Investimento"
                Conta conta = bancoDados.buscarConta(accountId);
                if (conta == null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "Conta não encontrada");
                    sendJsonResponse(exchange, 400, response);
                    return;
                }
                
                if (!conta.getTipo().equalsIgnoreCase("Investimento")) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "Apenas contas do tipo 'Investimento' podem ser utilizadas para criar investimentos");
                    sendJsonResponse(exchange, 400, response);
                    return;
                }
                
                // A corretora é o nome da conta de investimento
                String corretoraFinal = conta.getNome();
                
                // Verifica se o preço foi fornecido manualmente
                double precoAporte = 0.0;
                String nomeAtivo = null;
                
                if (data.containsKey("precoAporte")) {
                    // Preço fornecido manualmente
                    precoAporte = ((Number) data.get("precoAporte")).doubleValue();
                    if (data.containsKey("nomeAtivo")) {
                        nomeAtivo = (String) data.get("nomeAtivo");
                    }
                } else {
                    // Busca cotação do dia do aporte ou atual
                    QuoteService quoteService = QuoteService.getInstance();
                    QuoteService.QuoteResult quote = quoteService.getQuote(nome, categoria, dataAporte);
                    
                    if (!quote.success) {
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", false);
                        response.put("message", "Erro ao buscar cotação: " + quote.message);
                        sendJsonResponse(exchange, 400, response);
                        return;
                    }
                    
                    precoAporte = quote.price;
                    nomeAtivo = quote.assetName;
                    
                    // Converte para moeda do investimento se necessário
                    if (!moeda.equals(quote.currency)) {
                        double exchangeRate = quoteService.getExchangeRate(quote.currency, moeda);
                        precoAporte *= exchangeRate;
                    }
                }
                
                // Valida preço
                if (precoAporte <= 0) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "O preço de aporte deve ser maior que zero");
                    sendJsonResponse(exchange, 400, response);
                    return;
                }
                
                int investmentId = bancoDados.cadastrarInvestimento(nome, nomeAtivo, categoria, quantidade, 
                                                                  precoAporte, corretagem, corretoraFinal,
                                                                  dataAporte, userId, accountId, moeda);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Investimento cadastrado com sucesso");
                response.put("investmentId", investmentId);
                response.put("precoAporte", precoAporte);
                
                sendJsonResponse(exchange, 201, response);
            } catch (Exception e) {
                e.printStackTrace();
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Erro: " + e.getMessage());
                sendJsonResponse(exchange, 400, response);
            }
        }
        
        private void handleUpdateInvestment(HttpExchange exchange) throws IOException {
            try {
                String requestBody = readRequestBody(exchange);
                Map<String, Object> data = parseJsonWithNested(requestBody);
                
                int idInvestimento = ((Number) data.get("id")).intValue();
                String nome = (String) data.get("nome");
                String categoria = (String) data.get("categoria");
                double quantidade = ((Number) data.get("quantidade")).doubleValue();
                double corretagem = ((Number) data.get("corretagem")).doubleValue();
                
                // Valida quantidade mínima
                if (quantidade <= 0) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "A quantidade deve ser maior que zero");
                    sendJsonResponse(exchange, 400, response);
                    return;
                }
                String corretora = (String) data.get("corretora");
                String dataAporteStr = (String) data.get("dataAporte");
                LocalDate dataAporte = LocalDate.parse(dataAporteStr);
                String moeda = (String) data.getOrDefault("moeda", "BRL");
                
                // Busca cotação se necessário
                double precoAporte = data.containsKey("precoAporte") ? 
                    ((Number) data.get("precoAporte")).doubleValue() : 0.0;
                
                if (precoAporte == 0.0) {
                    QuoteService quoteService = QuoteService.getInstance();
                    QuoteService.QuoteResult quote = quoteService.getQuote(nome, categoria, dataAporte);
                    if (quote.success) {
                        precoAporte = quote.price;
                    }
                }
                
                bancoDados.atualizarInvestimento(idInvestimento, nome, categoria, quantidade,
                                                precoAporte, corretagem, corretora, dataAporte, moeda);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Investimento atualizado com sucesso");
                
                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                sendJsonResponse(exchange, 400, response);
            }
        }
        
        private void handleDeleteInvestment(HttpExchange exchange) throws IOException {
            try {
                String idParam = getQueryParam(exchange, "id");
                if (idParam == null) {
                    sendErrorResponse(exchange, 400, "ID do investimento não fornecido");
                    return;
                }
                
                int idInvestimento = Integer.parseInt(idParam);
                bancoDados.excluirInvestimento(idInvestimento);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Investimento excluído com sucesso");
                
                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                sendJsonResponse(exchange, 400, response);
            }
        }
    }
    
    static class InvestmentQuoteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "Método não permitido");
                return;
            }
            
            try {
                String symbol = getQueryParam(exchange, "symbol");
                String category = getQueryParam(exchange, "category");
                String dateStr = getQueryParam(exchange, "date");
                
                if (symbol == null || category == null) {
                    sendErrorResponse(exchange, 400, "Parâmetros 'symbol' e 'category' são obrigatórios");
                    return;
                }
                
                LocalDate date = dateStr != null ? LocalDate.parse(dateStr) : null;
                
                QuoteService quoteService = QuoteService.getInstance();
                QuoteService.QuoteResult quote = quoteService.getQuote(symbol, category, date);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", quote.success);
                response.put("message", quote.message);
                response.put("price", quote.price);
                response.put("currency", quote.currency);
                
                sendJsonResponse(exchange, quote.success ? 200 : 400, response);
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorResponse(exchange, 500, "Erro interno do servidor: " + e.getMessage());
            }
        }
    }
    
    // ===== UTILITY METHODS =====
    
    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), "UTF-8");
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
    
    /**
     * Parse JSON com suporte a objetos aninhados
     * Retorna Map<String, Object> onde valores podem ser String, Number ou Map aninhado
     */
    private static Map<String, Object> parseJsonWithNested(String json) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            json = json.trim();
            if (json.startsWith("{")) {
                json = json.substring(1);
            }
            if (json.endsWith("}")) {
                json = json.substring(0, json.length() - 1);
            }
            
            // Parse manual simples para suportar objetos aninhados
            int i = 0;
            while (i < json.length()) {
                // Encontra próxima chave
                int keyStart = json.indexOf("\"", i);
                if (keyStart == -1) break;
                int keyEnd = json.indexOf("\"", keyStart + 1);
                if (keyEnd == -1) break;
                
                String key = json.substring(keyStart + 1, keyEnd);
                
                // Encontra valor após o ":"
                int colonIndex = json.indexOf(":", keyEnd);
                if (colonIndex == -1) break;
                
                i = colonIndex + 1;
                while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
                    i++;
                }
                
                // Determina o tipo de valor
                if (i >= json.length()) break;
                
                char firstChar = json.charAt(i);
                Object value = null;
                
                if (firstChar == '\"') {
                    // String value
                    int valueEnd = json.indexOf("\"", i + 1);
                    if (valueEnd != -1) {
                        value = json.substring(i + 1, valueEnd);
                        i = valueEnd + 1;
                    }
                } else if (firstChar == '{') {
                    // Nested object
                    int braceCount = 1;
                    int objStart = i;
                    i++;
                    while (i < json.length() && braceCount > 0) {
                        if (json.charAt(i) == '{') braceCount++;
                        else if (json.charAt(i) == '}') braceCount--;
                        i++;
                    }
                    String nestedJson = json.substring(objStart, i);
                    value = parseJsonWithNested(nestedJson);
                } else if (firstChar == '[') {
                    // Array - parse como List
                    int bracketCount = 1;
                    int arrayStart = i;
                    i++;
                    while (i < json.length() && bracketCount > 0) {
                        if (json.charAt(i) == '[') bracketCount++;
                        else if (json.charAt(i) == ']') bracketCount--;
                        i++;
                    }
                    String arrayJson = json.substring(arrayStart, i);
                    value = parseJsonArray(arrayJson);
                } else {
                    // Número ou boolean
                    int valueEnd = i;
                    while (valueEnd < json.length() && 
                           json.charAt(valueEnd) != ',' && 
                           json.charAt(valueEnd) != '}') {
                        valueEnd++;
                    }
                    String valueStr = json.substring(i, valueEnd).trim();
                    
                    // Tenta converter para número
                    try {
                        if (valueStr.contains(".")) {
                            value = Double.parseDouble(valueStr);
                        } else {
                            value = Integer.parseInt(valueStr);
                        }
                    } catch (NumberFormatException e) {
                        value = valueStr;
                    }
                    
                    i = valueEnd;
                }
                
                if (value != null) {
                    result.put(key, value);
                }
                
                // Pula vírgula se houver
                while (i < json.length() && (json.charAt(i) == ',' || Character.isWhitespace(json.charAt(i)))) {
                    i++;
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao fazer parse do JSON aninhado: " + e.getMessage());
            System.err.println("JSON recebido: " + json);
            e.printStackTrace();
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
    
    /**
     * Parse um array JSON para List<Object>
     */
    @SuppressWarnings("unchecked")
    private static List<Object> parseJsonArray(String json) {
        List<Object> result = new ArrayList<>();
        
        try {
            json = json.trim();
            if (json.startsWith("[")) {
                json = json.substring(1);
            }
            if (json.endsWith("]")) {
                json = json.substring(0, json.length() - 1);
            }
            
            if (json.trim().isEmpty()) {
                return result;
            }
            
            // Parse manual dos elementos do array
            int i = 0;
            while (i < json.length()) {
                // Pula espaços
                while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
                    i++;
                }
                if (i >= json.length()) break;
                
                char firstChar = json.charAt(i);
                Object value = null;
                
                if (firstChar == '\"') {
                    // String value
                    int valueEnd = json.indexOf("\"", i + 1);
                    if (valueEnd != -1) {
                        value = json.substring(i + 1, valueEnd);
                        i = valueEnd + 1;
                    }
                } else if (firstChar == '{') {
                    // Nested object
                    int braceCount = 1;
                    int objStart = i;
                    i++;
                    while (i < json.length() && braceCount > 0) {
                        if (json.charAt(i) == '{') braceCount++;
                        else if (json.charAt(i) == '}') braceCount--;
                        i++;
                    }
                    String nestedJson = json.substring(objStart, i);
                    value = parseJsonWithNested(nestedJson);
                } else if (firstChar == '[') {
                    // Nested array
                    int bracketCount = 1;
                    int arrayStart = i;
                    i++;
                    while (i < json.length() && bracketCount > 0) {
                        if (json.charAt(i) == '[') bracketCount++;
                        else if (json.charAt(i) == ']') bracketCount--;
                        i++;
                    }
                    String nestedArrayJson = json.substring(arrayStart, i);
                    value = parseJsonArray(nestedArrayJson);
                } else {
                    // Número ou boolean
                    int valueEnd = i;
                    while (valueEnd < json.length() && 
                           json.charAt(valueEnd) != ',' && 
                           json.charAt(valueEnd) != ']') {
                        valueEnd++;
                    }
                    String valueStr = json.substring(i, valueEnd).trim();
                    
                    // Tenta converter para número
                    try {
                        if (valueStr.contains(".")) {
                            value = Double.parseDouble(valueStr);
                        } else {
                            value = Integer.parseInt(valueStr);
                        }
                    } catch (NumberFormatException e) {
                        if (valueStr.equalsIgnoreCase("true")) {
                            value = true;
                        } else if (valueStr.equalsIgnoreCase("false")) {
                            value = false;
                        } else if (valueStr.equalsIgnoreCase("null")) {
                            value = null;
                        } else {
                            value = valueStr;
                        }
                    }
                    
                    i = valueEnd;
                }
                
                if (value != null) {
                    result.add(value);
                }
                
                // Pula vírgula se houver
                while (i < json.length() && (json.charAt(i) == ',' || Character.isWhitespace(json.charAt(i)))) {
                    i++;
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao fazer parse do array JSON: " + e.getMessage());
            System.err.println("JSON recebido: " + json);
        }
        
        return result;
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
        
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        
        exchange.sendResponseHeaders(statusCode, jsonResponse.getBytes("UTF-8").length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(jsonResponse.getBytes("UTF-8"));
        }
    }
    
    private static void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        String jsonResponse = toJson(response);
        
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        
        exchange.sendResponseHeaders(statusCode, jsonResponse.getBytes("UTF-8").length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(jsonResponse.getBytes("UTF-8"));
        }
    }
}