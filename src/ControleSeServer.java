import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.security.KeyStore;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.*;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

/**
 * Servidor HTTP simplificado para conectar Frontend com Backend Java
 * Implementa uma API REST básica para o sistema Controle-se
 */
public class ControleSeServer {
    private static final Logger LOGGER = Logger.getLogger(ControleSeServer.class.getName());
    private static BancoDadosPostgreSQL bancoDados;
    private static HttpServer server;
    private static final int PORT = 8080;
    
    // Cache simples para dados que mudam pouco (TTL de 30 segundos)
    private static final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30 * 1000; // 30 segundos
    
    private static class CacheEntry {
        final Object data;
        final long timestamp;
        
        CacheEntry(Object data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > CACHE_TTL_MS;
        }
    }
    
    private static <T> T getCached(String key) {
        CacheEntry entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            return (T) entry.data;
        }
        if (entry != null) {
            cache.remove(key); // Remove entrada expirada
        }
        return null;
    }
    
    private static void setCached(String key, Object data) {
        cache.put(key, new CacheEntry(data));
    }
    
    private static void invalidateCache(String pattern) {
        if (pattern == null) {
            cache.clear();
        } else {
            cache.entrySet().removeIf(entry -> entry.getKey().startsWith(pattern));
        }
    }
    
    /**
     * Normaliza números no formato brasileiro (vírgula como separador decimal) 
     * para formato internacional (ponto como separador decimal)
     * Exemplos: "1.234,56" -> "1234.56", "1,5" -> "1.5", "1000" -> "1000"
     */
    private static String normalizeBrazilianNumber(String numberStr) {
        if (numberStr == null || numberStr.trim().isEmpty()) {
            return numberStr;
        }
        String trimmed = numberStr.trim();
        // Remove separadores de milhar (pontos) e substitui vírgula por ponto
        // Exemplo: "1.234,56" -> remove pontos -> "1234,56" -> substitui vírgula -> "1234.56"
        if (trimmed.contains(",")) {
            // Tem vírgula: formato brasileiro
            // Remove pontos (separadores de milhar) e substitui vírgula por ponto
            return trimmed.replace(".", "").replace(",", ".");
        } else if (trimmed.contains(".")) {
            // Tem ponto mas não vírgula: pode ser formato internacional ou separador de milhar
            // Se tiver mais de um ponto, provavelmente é separador de milhar (formato brasileiro sem vírgula)
            // Exemplo: "1.234" -> "1234"
            long dotCount = trimmed.chars().filter(ch -> ch == '.').count();
            if (dotCount > 1) {
                // Múltiplos pontos: remove todos (separadores de milhar)
                return trimmed.replace(".", "");
            }
            // Um ponto: pode ser decimal, mantém
            return trimmed;
        }
        // Sem ponto nem vírgula: número inteiro
        return trimmed;
    }
    
    /**
     * Parse de número com suporte a formato brasileiro
     */
    private static double parseDoubleBrazilian(String numberStr) throws NumberFormatException {
        String normalized = normalizeBrazilianNumber(numberStr);
        return Double.parseDouble(normalized);
    }
    
    static {
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new PlainLogFormatter());
        handler.setLevel(Level.INFO);
        LOGGER.setUseParentHandlers(false);
        LOGGER.addHandler(handler);
        LOGGER.setLevel(Level.INFO);
    }
    
    public static void main(String[] args) {
        try {
            // Inicializa o banco de dados PostgreSQL (Aiven)
            bancoDados = new BancoDadosPostgreSQL();
            
            // Cria o servidor HTTP
            server = createServer();
            
            // Configura as rotas da API
            setupRoutes();
            
            // Inicia o servidor
            server.start();
            
            // Inicia o scheduler de recorrências (executa diariamente)
            iniciarSchedulerRecorrencias();
            
            // Inicia o scheduler de atualização de cotações (executa a cada 30 minutos)
            iniciarSchedulerCotacoes();
            
            // Exibe informações do servidor
            exibirInformacoesServidor();
            
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erro ao iniciar servidor", e);
        }
    }
    
    private static HttpServer createServer() throws IOException {
        String keystorePath = System.getenv("TLS_KEYSTORE_PATH");
        String keystorePassword = System.getenv("TLS_KEYSTORE_PASSWORD");
        String keystoreType = System.getenv("TLS_KEYSTORE_TYPE");
        if (keystoreType == null || keystoreType.isBlank()) {
            keystoreType = "PKCS12";
        }
        
        if (keystorePath != null && !keystorePath.isBlank() &&
            keystorePassword != null && !keystorePassword.isBlank()) {
            try (FileInputStream fis = new FileInputStream(keystorePath)) {
                KeyStore keyStore = KeyStore.getInstance(keystoreType);
                char[] passwordChars = keystorePassword.toCharArray();
                keyStore.load(fis, passwordChars);
                
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, passwordChars);
                
                // Usa TrustManager padrão do sistema (confia em CAs padrão)
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init((KeyStore) null); // Usa truststore padrão do sistema
                
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
                
                HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(PORT), 0);
                httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                    @Override
                    public void configure(HttpsParameters params) {
                        try {
                            SSLParameters sslParams = sslContext.getDefaultSSLParameters();
                            // Configura para não exigir autenticação de cliente (modo servidor padrão)
                            sslParams.setNeedClientAuth(false);
                            sslParams.setWantClientAuth(false);
                            params.setSSLParameters(sslParams);
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "Erro ao configurar parâmetros SSL, usando padrão", e);
                            params.setSSLParameters(sslContext.getDefaultSSLParameters());
                        }
                    }
                });
                LOGGER.info("HTTPS habilitado com certificado fornecido: " + keystorePath);
                return httpsServer;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Falha ao configurar HTTPS", e);
                throw new IOException("Não foi possível iniciar servidor HTTPS", e);
            }
        }
        
        LOGGER.warning("Variáveis TLS não configuradas. Servidor rodando em HTTP (somente para desenvolvimento).");
        return HttpServer.create(new InetSocketAddress(PORT), 0);
    }
    
    /**
     * Exibe informações do servidor
     */
    private static void exibirInformacoesServidor() {
        LOGGER.info("=== SERVIDOR CONTROLE-SE INICIADO ===");
        LOGGER.info("Servidor rodando em: http" + (server instanceof HttpsServer ? "s" : "") + "://localhost:" + PORT);
        LOGGER.info("Frontend disponível em: http" + (server instanceof HttpsServer ? "s" : "") + "://localhost:" + PORT + "/");
        LOGGER.info("API disponível em: http" + (server instanceof HttpsServer ? "s" : "") + "://localhost:" + PORT + "/api/");
        LOGGER.info("Pressione Ctrl+C para parar o servidor");
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
                    LOGGER.info("=== SCHEDULER DE RECORRÊNCIAS === Executando processamento automático...");
                    int criados = bancoDados.processarRecorrencias();
                    LOGGER.info("Total de transações recorrentes criadas: " + criados);
                } catch (Exception e) {
                    System.err.println("Erro ao processar recorrências: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    // IMPORTANTE: Fechar conexão após a execução da tarefa agendada
                    if (bancoDados != null) {
                        bancoDados.closeConnection();
                    }
                }
            }
        }, delay, periodo);
        
        // Para fins de DEBUG/TESTE: executa imediatamente na inicialização também
        LOGGER.info("[INICIALIZAÇÃO] Processando recorrências pendentes...");
        try {
            int criados = bancoDados.processarRecorrencias();
            if (criados > 0) {
                LOGGER.info("[INICIALIZAÇÃO] " + criados + " transações recorrentes criadas");
            } else {
                LOGGER.info("[INICIALIZAÇÃO] Nenhuma recorrência pendente");
            }
        } catch (Exception e) {
            System.err.println("[INICIALIZAÇÃO] Erro ao processar recorrências: " + e.getMessage());
        } finally {
            // IMPORTANTE: Fechar conexão após a execução inicial
            if (bancoDados != null) {
                bancoDados.closeConnection();
            }
        }
        LOGGER.fine("Scheduler de recorrências inicializado");
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
                    LOGGER.info("=== ATUALIZAÇÃO DE COTAÇÕES ===");
                    QuoteService quoteService = QuoteService.getInstance();
                    quoteService.cleanExpiredCache();
                    LOGGER.info("Cache de cotações limpo e atualizado");
                } catch (Exception e) {
                    System.err.println("Erro ao atualizar cotações: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, 0, periodo);
        
        LOGGER.info("[INICIALIZAÇÃO] Scheduler de cotações iniciado (atualiza a cada 30 minutos)");
    }
    
    private static void setupRoutes() {
        // API Routes básicas (devem vir antes do StaticFileHandler)
        server.createContext("/api/auth/login", new LoginHandler());
        server.createContext("/api/auth/register", new RegisterHandler());
        server.createContext("/api/auth/change-password", secure(new ChangePasswordHandler()));
        server.createContext("/api/auth/user", secure(new DeleteUserHandler()));
        server.createContext("/api/dashboard/overview", secure(new OverviewHandler()));
        server.createContext("/api/categories", secure(new CategoriesHandler()));
        server.createContext("/api/accounts", secure(new AccountsHandler()));
        server.createContext("/api/transactions/recent", secure(new RecentTransactionsHandler()));
        server.createContext("/api/transactions", secure(new TransactionsHandler()));
        server.createContext("/api/expenses", secure(new ExpensesHandler()));
        server.createContext("/api/incomes", secure(new IncomesHandler()));
        server.createContext("/api/budgets", secure(new BudgetsHandler()));
        server.createContext("/api/tags", secure(new TagsHandler()));
        server.createContext("/api/reports", secure(new ReportsHandler()));
        server.createContext("/api/investments", secure(new InvestmentsHandler()));
        server.createContext("/api/investments/evolution", secure(new InvestmentEvolutionHandler()));
        server.createContext("/api/investments/quote", secure(new InvestmentQuoteHandler()));
        
        // Health check
        server.createContext("/health", new HealthHandler());
        
        // Servir arquivos estáticos (HTML, CSS, JS) - deve vir por último
        server.createContext("/", new StaticFileHandler());
        
        // Configura executor com pool de threads para múltiplos usuários simultâneos
        // Thread pool reduzido para evitar estourar limite de conexões do banco gratuito
        ExecutorService executor = new ThreadPoolExecutor(
            2,                       // corePoolSize: mínimo de threads
            5,                       // maximumPoolSize: máximo de threads (limitado pelo banco)
            60L,                     // keepAliveTime: 60 segundos
            TimeUnit.SECONDS,        // unidade de tempo
            new LinkedBlockingQueue<>(500), // fila de requisições (limite de 500)
            new ThreadFactory() {
                private int threadNumber = 1;
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "ControleSe-Handler-" + threadNumber++);
                    t.setDaemon(false);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // se fila cheia, executa na thread chamadora
        );
        server.setExecutor(executor);
    }
    
    // ===== HANDLERS SIMPLIFICADOS =====
    
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            
            // Never handle API routes here
            if (path.startsWith("/api/")) {
                sendErrorResponse(exchange, 404, "Not found");
                return;
            }
            
            // Check if dist directory exists (React build)
            File distDir = new File("dist");
            boolean distExists = distDir.exists() && distDir.isDirectory();
            
            // Check if path is a static file (has file extension)
            boolean isStaticFile = path.contains(".") && 
                (path.endsWith(".html") || path.endsWith(".js") || path.endsWith(".css") || 
                 path.endsWith(".json") || path.endsWith(".png") || path.endsWith(".jpg") || 
                 path.endsWith(".jpeg") || path.endsWith(".svg") || path.endsWith(".woff") || 
                 path.endsWith(".woff2") || path.endsWith(".ttf") || path.endsWith(".map") ||
                 path.endsWith(".ico") || path.startsWith("/assets/"));
            
            try {
                File file = null;
                
                // If it's a static file, try to serve it
                if (isStaticFile) {
                    // Try React build directory first if it exists
                    if (distExists) {
                        File distFile = new File("dist" + path);
                        if (distFile.exists() && distFile.isFile()) {
                            file = distFile;
                        }
                    }
                    
                    // If not found in dist, try root directory
                    if (file == null) {
                        File rootFile = new File("." + path);
                        if (rootFile.exists() && rootFile.isFile()) {
                            file = rootFile;
                        }
                    }
                }
                
                // If not a static file or file not found, serve index.html for SPA routing
                if (file == null) {
                    if (distExists) {
                        File distIndex = new File("dist/index.html");
                        if (distIndex.exists()) {
                            file = distIndex;
                        }
                    }
                    if (file == null) {
                        File rootIndex = new File("./index.html");
                        if (rootIndex.exists()) {
                            file = rootIndex;
                        }
                    }
                }
                
                if (file != null && file.exists() && file.isFile()) {
                    // Always serve index.html as HTML, even if path doesn't end with .html
                    String contentType = file.getName().equals("index.html") 
                        ? "text/html; charset=utf-8" 
                        : getContentType(path);
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
                e.printStackTrace(); // Log do erro para debug
                sendErrorResponse(exchange, 500, "Erro interno do servidor");
            } finally {
                // Garante que conexão da thread seja fechada após requisição
                bancoDados.closeConnection();
            }
        }
        
        private String getContentType(String path) {
            if (path.endsWith(".html")) return "text/html; charset=utf-8";
            if (path.endsWith(".css")) return "text/css; charset=utf-8";
            if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
            if (path.endsWith(".json")) return "application/json; charset=utf-8";
            if (path.endsWith(".png")) return "image/png";
            if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
            if (path.endsWith(".svg")) return "image/svg+xml";
            if (path.endsWith(".woff")) return "font/woff";
            if (path.endsWith(".woff2")) return "font/woff2";
            if (path.endsWith(".ttf")) return "font/ttf";
            if (path.endsWith(".map")) return "application/json";
            return "text/plain; charset=utf-8";
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
                    String token = JwtUtil.generateToken(usuario);
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("user", Map.of(
                        "id", usuario.getIdUsuario(),
                        "name", usuario.getNome(),
                        "email", usuario.getEmail(),
                        "token", token
                    ));
                    
                    sendJsonResponse(exchange, 200, response);
                } else {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "Email ou senha incorretos");
                    
                    sendJsonResponse(exchange, 401, response);
                }
            } catch (Exception e) {
                e.printStackTrace(); // Log do erro para debug
                sendErrorResponse(exchange, 500, "Erro interno do servidor");
            } finally {
                // Garante que conexão da thread seja fechada após requisição
                bancoDados.closeConnection();
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
                
                // Busca o usuário recém-cadastrado para retornar os dados completos
                Usuario usuario = bancoDados.buscarUsuario(userId);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Usuário cadastrado com sucesso");
                response.put("user", Map.of(
                    "id", usuario.getIdUsuario(),
                    "name", usuario.getNome(),
                    "email", usuario.getEmail(),
                    "token", JwtUtil.generateToken(usuario)
                ));
                
                sendJsonResponse(exchange, 201, response);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                
                sendJsonResponse(exchange, 400, response);
            } finally {
                // Garante que conexão da thread seja fechada após requisição
                bancoDados.closeConnection();
            }
        }
    }

    static class ChangePasswordHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "Método não permitido");
                return;
            }

            try {
                int userId = requireUserId(exchange);
                String requestBody = readRequestBody(exchange);
                Map<String, String> data = parseJson(requestBody);

                String currentPassword = data.get("currentPassword");
                String newPassword = data.get("newPassword");

                if (currentPassword == null || currentPassword.isBlank()) {
                    sendErrorResponse(exchange, 400, "Senha atual é obrigatória");
                    return;
                }
                if (newPassword == null || newPassword.isBlank()) {
                    sendErrorResponse(exchange, 400, "Nova senha é obrigatória");
                    return;
                }

                bancoDados.atualizarSenhaUsuario(userId, currentPassword, newPassword);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Senha atualizada com sucesso");
                sendJsonResponse(exchange, 200, response);
            } catch (UnauthorizedException e) {
                handleUnauthorized(exchange, e);
            } catch (IllegalArgumentException e) {
                sendErrorResponse(exchange, 400, e.getMessage());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Erro ao atualizar senha", e);
                sendErrorResponse(exchange, 500, "Erro interno do servidor");
            } finally {
                bancoDados.closeConnection();
            }
        }
    }

    static class DeleteUserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"DELETE".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "Método não permitido");
                return;
            }

            try {
                int userId = requireUserId(exchange);
                bancoDados.excluirUsuario(userId);
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Usuário excluído com sucesso");
                sendJsonResponse(exchange, 200, response);
            } catch (UnauthorizedException e) {
                handleUnauthorized(exchange, e);
            } catch (IllegalArgumentException e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Usuário já havia sido removido");
                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Erro ao excluir usuário", e);
                sendErrorResponse(exchange, 500, "Erro interno do servidor");
            } finally {
                bancoDados.closeConnection();
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
                int userId = requireUserId(exchange);
                
                // Cache de valores totais (TTL curto pois podem mudar com frequência)
                String cacheKeyIncome = "totalIncome_" + userId;
                String cacheKeyExpense = "totalExpense_" + userId;
                String cacheKeyBalance = "balance_" + userId;
                String cacheKeyCredito = "totalCredito_" + userId;
                
                Double totalIncome = getCached(cacheKeyIncome);
                Double totalExpense = getCached(cacheKeyExpense);
                Double balance = getCached(cacheKeyBalance);
                Double totalCreditoDisponivel = getCached(cacheKeyCredito);
                
                if (totalIncome == null) {
                    totalIncome = bancoDados.calcularTotalReceitasUsuario(userId);
                    setCached(cacheKeyIncome, totalIncome);
                }
                if (totalExpense == null) {
                    totalExpense = bancoDados.calcularTotalGastosUsuario(userId);
                    setCached(cacheKeyExpense, totalExpense);
                }
                if (balance == null) {
                    balance = bancoDados.calcularSaldoContasUsuarioSemInvestimento(userId);
                    setCached(cacheKeyBalance, balance);
                }
                if (totalCreditoDisponivel == null) {
                    totalCreditoDisponivel = bancoDados.calcularTotalCreditoDisponivelCartoes(userId);
                    setCached(cacheKeyCredito, totalCreditoDisponivel);
                }
                
                // Calcula informações agregadas de faturas de cartões de crédito
                // Reutiliza lista de contas do cache se disponível
                String cacheKeyAccounts = "accounts_" + userId;
                List<Conta> allAccounts = getCached(cacheKeyAccounts);
                if (allAccounts == null) {
                    allAccounts = bancoDados.buscarContasPorUsuario(userId);
                    setCached(cacheKeyAccounts, allAccounts);
                }
                
                List<Conta> contasCartao = new ArrayList<>(allAccounts);
                contasCartao.removeIf(c -> !c.isCartaoCredito() || c.getDiaFechamento() == null || c.getDiaPagamento() == null);
                
                Map<String, Object> cartoesInfo = null;
                if (!contasCartao.isEmpty()) {
                    // Encontra o próximo pagamento mais próximo entre todos os cartões
                    LocalDate proximoPagamentoMaisProximo = null;
                    LocalDate proximoFechamentoMaisProximo = null;
                    long menorDiasAtePagamento = Long.MAX_VALUE;
                    
                    for (Conta cartao : contasCartao) {
                        Map<String, Object> faturaInfo = calcularInfoFatura(cartao.getDiaFechamento(), cartao.getDiaPagamento());
                        long diasAtePagamento = (Long) faturaInfo.get("diasAtePagamento");
                        
                        if (diasAtePagamento < menorDiasAtePagamento) {
                            menorDiasAtePagamento = diasAtePagamento;
                            proximoPagamentoMaisProximo = LocalDate.parse((String) faturaInfo.get("proximoPagamento"));
                            proximoFechamentoMaisProximo = LocalDate.parse((String) faturaInfo.get("proximoFechamento"));
                        }
                    }
                    
                    if (proximoPagamentoMaisProximo != null) {
                        cartoesInfo = new HashMap<>();
                        cartoesInfo.put("proximoPagamento", proximoPagamentoMaisProximo.toString());
                        cartoesInfo.put("proximoFechamento", proximoFechamentoMaisProximo.toString());
                        cartoesInfo.put("diasAtePagamento", menorDiasAtePagamento);
                        cartoesInfo.put("diasAteFechamento", ChronoUnit.DAYS.between(LocalDate.now(), proximoFechamentoMaisProximo));
                    }
                }
                
                // Calcula valor total dos investimentos (com cache)
                String cacheKeyInvestments = "totalInvestments_" + userId;
                Double totalInvestmentsValue = getCached(cacheKeyInvestments);
                
                if (totalInvestmentsValue == null) {
                    totalInvestmentsValue = 0.0;
                    List<Investimento> investments = bancoDados.buscarInvestimentosPorUsuario(userId);
                    
                    if (!investments.isEmpty()) {
                        QuoteService quoteService = QuoteService.getInstance();
                        
                        for (Investimento inv : investments) {
                            // Busca cotação atual
                            QuoteService.QuoteResult quote = quoteService.getQuote(inv.getNome(), inv.getCategoria(), null);
                            double currentPrice = quote != null && quote.success ? quote.price : inv.getPrecoAporte();
                            
                            // Converte para BRL se necessário
                            if (quote != null && quote.success && !"BRL".equals(quote.currency)) {
                                double exchangeRate = quoteService.getExchangeRate(quote.currency, "BRL");
                                currentPrice *= exchangeRate;
                            } else if (!"BRL".equals(inv.getMoeda())) {
                                // Se não houve cotação ou é fallback, e o ativo original não é BRL
                                double exchangeRate = quoteService.getExchangeRate(inv.getMoeda(), "BRL");
                                currentPrice *= exchangeRate;
                            }
                            
                            totalInvestmentsValue += inv.getQuantidade() * currentPrice;
                        }
                    }
                    
                    // Cache por 5 minutos (investimentos mudam menos frequentemente)
                    setCached(cacheKeyInvestments, totalInvestmentsValue);
                }
                
                String cacheKeyTotalAccounts = "totalAccounts_" + userId;
                Double totalAccounts = getCached(cacheKeyTotalAccounts);
                if (totalAccounts == null) {
                    totalAccounts = bancoDados.calcularTotalSaldoContasUsuario(userId);
                    setCached(cacheKeyTotalAccounts, totalAccounts);
                }
                
                double netWorth = totalAccounts + totalInvestmentsValue; // Patrimônio = Contas + Ativos
                
                // Get category breakdown - OTIMIZADO: usa query agregada para evitar N+1 queries
                String cacheKey = "categories_" + userId;
                List<Categoria> categories = getCached(cacheKey);
                if (categories == null) {
                    categories = bancoDados.buscarCategoriasPorUsuario(userId);
                    setCached(cacheKey, categories);
                }
                
                // Busca todos os gastos por categoria de uma vez (query agregada)
                Map<Integer, Double> gastosPorCategoria = bancoDados.calcularTotalGastosPorTodasCategoriasEUsuario(userId);
                
                // Busca a categoria "Sem Categoria" para incluir nos relatórios se houver gastos
                int idCategoriaSemCategoria = bancoDados.obterOuCriarCategoriaSemCategoria(userId);
                Categoria categoriaSemCategoria = bancoDados.buscarCategoria(idCategoriaSemCategoria);
                
                List<Map<String, Object>> categoryBreakdown = new ArrayList<>();
                for (Categoria categoria : categories) {
                    Double categoryTotal = gastosPorCategoria.get(categoria.getIdCategoria());
                    if (categoryTotal != null && categoryTotal > 0) {
                        Map<String, Object> categoryData = new HashMap<>();
                        categoryData.put("name", categoria.getNome());
                        categoryData.put("value", categoryTotal);
                        categoryBreakdown.add(categoryData);
                    }
                }
                
                // Inclui "Sem Categoria" nos relatórios se houver gastos nela
                if (categoriaSemCategoria != null) {
                    Double semCategoriaTotal = gastosPorCategoria.get(idCategoriaSemCategoria);
                    if (semCategoriaTotal != null && semCategoriaTotal > 0) {
                        Map<String, Object> categoryData = new HashMap<>();
                        categoryData.put("name", categoriaSemCategoria.getNome());
                        categoryData.put("value", semCategoriaTotal);
                        categoryBreakdown.add(categoryData);
                    }
                }
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("totalIncome", totalIncome);
                dataMap.put("totalExpense", totalExpense);
                dataMap.put("balance", balance);
                dataMap.put("netWorth", netWorth);
                dataMap.put("totalCreditoDisponivel", totalCreditoDisponivel);
                if (cartoesInfo != null) {
                    dataMap.put("cartoesInfo", cartoesInfo);
                }
                dataMap.put("categoryBreakdown", categoryBreakdown);
                response.put("data", dataMap);
                
                sendJsonResponse(exchange, 200, response);
            } catch (UnauthorizedException e) {
                handleUnauthorized(exchange, e);
            } catch (Exception e) {
                e.printStackTrace(); // Log do erro para debug
                sendErrorResponse(exchange, 500, "Erro interno do servidor");
            } finally {
                // Garante que conexão da thread seja fechada após requisição
                bancoDados.closeConnection();
            }
        }
    }
    
    static class CategoriesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
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
            } catch (UnauthorizedException e) {
                handleUnauthorized(exchange, e);
            }
        }
        
        private void handleGetCategories(HttpExchange exchange) throws IOException {
            try {
                int userId = requireUserId(exchange);
                
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
            } catch (UnauthorizedException e) {
                handleUnauthorized(exchange, e);
            } catch (Exception e) {
                e.printStackTrace(); // Log do erro para debug
                sendErrorResponse(exchange, 500, "Erro interno do servidor");
            } finally {
                // Garante que conexão da thread seja fechada após requisição
                bancoDados.closeConnection();
            }
        }
        
        private void handleCreateCategory(HttpExchange exchange) throws IOException {
            try {
                int userId = requireUserId(exchange);
                String requestBody = readRequestBody(exchange);
                Map<String, Object> data = parseJsonWithNested(requestBody);
                
                String name = (String) data.get("name");
                if (name == null) {
                    name = (String) data.get("nome");
                }
                
                int categoryId = bancoDados.cadastrarCategoria(name, userId);
                
                // Verifica se deve criar orçamento junto
                Integer budgetId = null;
                if (data.containsKey("budget") && data.get("budget") != null) {
                    Object budgetObj = data.get("budget");
                    double value = 0.0;
                    String period = "MENSAL";
                    
                    if (budgetObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> budgetData = (Map<String, Object>) budgetObj;
                        
                        Object valueObj = budgetData.get("value");
                        if (valueObj instanceof Number) {
                            value = ((Number) valueObj).doubleValue();
                        } else if (valueObj instanceof String) {
                            value = parseDoubleBrazilian((String) valueObj);
                        }
                        
                        if (budgetData.containsKey("period")) {
                            period = (String) budgetData.get("period");
                        }
                    } else if (budgetObj instanceof Number) {
                        value = ((Number) budgetObj).doubleValue();
                    } else if (budgetObj instanceof String) {
                        try {
                            value = parseDoubleBrazilian((String) budgetObj);
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }
                    
                    if (value > 0 && period != null && !period.isEmpty()) {
                        LOGGER.info("Criando orçamento automático para categoria " + categoryId + ": " + value);
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
            } catch (UnauthorizedException e) {
                handleUnauthorized(exchange, e);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                
                sendJsonResponse(exchange, 400, response);
            } finally {
                bancoDados.closeConnection();
            }
        }
        
        private void handleUpdateCategory(HttpExchange exchange) throws IOException {
            try {
                String requestBody = readRequestBody(exchange);
                Map<String, String> data = parseJson(requestBody);
                
                int categoryId = Integer.parseInt(data.get("id"));
                String newName = data.get("name");
                if (newName == null) newName = data.get("nome");
                
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
            } finally {
                bancoDados.closeConnection();
            }
        }
        
        private void handleDeleteCategory(HttpExchange exchange) throws IOException {
            try {
                String idParam = getQueryParam(exchange, "id");
                
                // Se não encontrou no query param, tenta pegar da URL
                if (idParam == null) {
                    String path = exchange.getRequestURI().getPath();
                    String[] segments = path.split("/");
                    // path esperado: /api/categories/123 -> ["", "api", "categories", "123"]
                    if (segments.length > 3) {
                        idParam = segments[segments.length - 1];
                    }
                }
                
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
            } finally {
                bancoDados.closeConnection();
            }
        }
    }
    
    static class AccountsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
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
            } catch (UnauthorizedException e) {
                handleUnauthorized(exchange, e);
            }
        }
        
        private void handleGetAccounts(HttpExchange exchange) throws IOException {
            try {
                String userIdParam = getQueryParam(exchange, "userId");
                int userId = userIdParam != null ? Integer.parseInt(userIdParam) : 1;
                
                // Cache de contas (dados que mudam pouco)
                String cacheKey = "accounts_" + userId;
                List<Conta> accounts = getCached(cacheKey);
                if (accounts == null) {
                    accounts = bancoDados.buscarContasPorUsuario(userId);
                    setCached(cacheKey, accounts);
                }
                
                // Verifica se há contas de investimento antes de buscar investimentos
                boolean hasInvestmentAccounts = accounts.stream().anyMatch(c -> {
                    String tipo = c.getTipo() != null ? c.getTipo().toLowerCase().trim() : "";
                    return tipo.equals("investimento") || tipo.startsWith("investimento");
                });
                
                // Busca investimentos apenas se houver contas de investimento
                List<Investimento> investments = new ArrayList<>();
                QuoteService quoteService = null;
                if (hasInvestmentAccounts) {
                    investments = bancoDados.buscarInvestimentosPorUsuario(userId);
                    quoteService = QuoteService.getInstance();
                }
                
                // Mapa para acumular valor dos investimentos por conta
                Map<Integer, Double> investimentoPorConta = new HashMap<>();
                
                // Só processa investimentos se houver
                if (hasInvestmentAccounts && quoteService != null) {
                    for (Investimento inv : investments) {
                    double currentPrice = 0.0;
                    double currentValue = 0.0;
                    
                    // Para renda fixa, calcula valor atual
                    if ("RENDA_FIXA".equals(inv.getCategoria())) {
                        // Converte aporte para BRL se necessário
                        double valorAporteBRL = inv.getValorAporte();
                        if (!"BRL".equals(inv.getMoeda())) {
                            double exchangeRate = quoteService.getExchangeRate(inv.getMoeda(), "BRL");
                            valorAporteBRL *= exchangeRate;
                        }
                        
                        currentValue = quoteService.calculateFixedIncomeValue(
                            valorAporteBRL,
                            inv.getTipoInvestimento(),
                            inv.getTipoRentabilidade(),
                            inv.getIndice(),
                            inv.getPercentualIndice(),
                            inv.getTaxaFixa(),
                            inv.getDataAporte(),
                            inv.getDataVencimento(),
                            LocalDate.now()
                        );
                    } else {
                        // Renda variável
                        QuoteService.QuoteResult quote = quoteService.getQuote(inv.getNome(), inv.getCategoria(), null);
                        currentPrice = quote != null && quote.success ? quote.price : inv.getPrecoAporte();
                        
                        if (quote != null && quote.success && !"BRL".equals(quote.currency)) {
                            double exchangeRate = quoteService.getExchangeRate(quote.currency, "BRL");
                            currentPrice *= exchangeRate;
                        } else if (!"BRL".equals(inv.getMoeda())) {
                            double exchangeRate = quoteService.getExchangeRate(inv.getMoeda(), "BRL");
                            currentPrice *= exchangeRate;
                        }
                        
                        currentValue = inv.getQuantidade() * currentPrice;
                    }
                    
                        int contaId = inv.getIdConta();
                        investimentoPorConta.put(contaId, investimentoPorConta.getOrDefault(contaId, 0.0) + currentValue);
                    }
                }
                
                List<Map<String, Object>> accountList = new ArrayList<>();
                
                for (Conta conta : accounts) {
                    Map<String, Object> accountData = new HashMap<>();
                    accountData.put("idConta", conta.getIdConta());
                    accountData.put("nome", conta.getNome());
                    accountData.put("tipo", conta.getTipo());
                    
                    double saldoExibicao = conta.getSaldoAtual();
                    
                    // Se for conta de investimento, substitui (ou soma?) o saldo pelo valor dos ativos
                    // O usuário pediu: "considere o valor investido como valor da conta"
                    // Assumindo que para conta de investimento, o valor relevante é o patrimônio nela
                    // Verifica se é conta de investimento (aceita "Investimento" ou "Investimento (Corretora)")
                    String tipoConta = conta.getTipo() != null ? conta.getTipo().toLowerCase().trim() : "";
                    if (tipoConta.equals("investimento") || tipoConta.equals("investimento (corretora)") || tipoConta.startsWith("investimento")) {
                        // Soma o saldo em conta (caixa) + valor dos ativos
                        double valorAtivos = investimentoPorConta.getOrDefault(conta.getIdConta(), 0.0);
                        // saldoExibicao = valorAtivos + conta.getSaldoAtual(); 
                        // Na verdade, se o sistema não controla o saldo de caixa da corretora automaticamente, 
                        // o saldoAtual do banco pode estar desatualizado ou ser apenas o "inicial".
                        // Mas geralmente conta de investimento tem "saldo em conta" + "meus investimentos".
                        // Vou somar os dois. Se o usuário não usa o saldo da conta, ele deve estar 0.
                        saldoExibicao = conta.getSaldoAtual() + valorAtivos;
                    }
                    
                    accountData.put("saldoAtual", saldoExibicao);
                    accountData.put("idUsuario", conta.getIdUsuario());
                    if (conta.getDiaFechamento() != null) {
                        accountData.put("diaFechamento", conta.getDiaFechamento());
                    }
                    if (conta.getDiaPagamento() != null) {
                        accountData.put("diaPagamento", conta.getDiaPagamento());
                    }
                    
                    // Calcula informações de fatura para cartões de crédito
                    if (conta.isCartaoCredito() && conta.getDiaFechamento() != null && conta.getDiaPagamento() != null) {
                        Map<String, Object> faturaInfo = calcularInfoFatura(conta.getDiaFechamento(), conta.getDiaPagamento());
                        accountData.put("faturaInfo", faturaInfo);
                    }
                    
                    accountList.add(accountData);
                }
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", accountList);
                
                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                e.printStackTrace(); // Log do erro para debug
                sendErrorResponse(exchange, 500, "Erro interno do servidor");
            } finally {
                // Garante que conexão da thread seja fechada após requisição
                bancoDados.closeConnection();
            }
        }
        
        private void handleCreateAccount(HttpExchange exchange) throws IOException {
            try {
                int userId = requireUserId(exchange);
                String requestBody = readRequestBody(exchange);
                Map<String, String> data = parseJson(requestBody);
                
                String name = data.get("name");
                if (name == null) name = data.get("nome");

                String type = data.get("type");
                if (type == null) type = data.get("tipo");
                
                String balanceStr = data.get("balance");
                if (balanceStr == null) {
                    balanceStr = data.get("saldoInicial");
                }
                if (balanceStr == null) {
                    throw new IllegalArgumentException("Saldo é obrigatório");
                }
                double balance = parseDoubleBrazilian(balanceStr);
                
                // Lê campos opcionais de cartão de crédito
                Integer diaFechamento = null;
                Integer diaPagamento = null;
                String diaFechamentoStr = data.get("diaFechamento");
                if (diaFechamentoStr != null && !diaFechamentoStr.isEmpty()) {
                    diaFechamento = Integer.parseInt(diaFechamentoStr);
                }
                String diaPagamentoStr = data.get("diaPagamento");
                if (diaPagamentoStr != null && !diaPagamentoStr.isEmpty()) {
                    diaPagamento = Integer.parseInt(diaPagamentoStr);
                }
                
                int accountId = bancoDados.cadastrarConta(name, type, balance, userId, diaFechamento, diaPagamento);
                
                // Invalida cache de contas e overview
                invalidateCache("accounts_" + userId);
                invalidateCache("overview_" + userId);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Conta criada com sucesso");
                response.put("accountId", accountId);
                
                sendJsonResponse(exchange, 201, response);
            } catch (UnauthorizedException e) {
                handleUnauthorized(exchange, e);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                
                sendJsonResponse(exchange, 400, response);
            } finally {
                bancoDados.closeConnection();
            }
        }
        
        private void handleUpdateAccount(HttpExchange exchange) throws IOException {
            try {
                requireUserId(exchange);
                String requestBody = readRequestBody(exchange);
                Map<String, String> data = parseJson(requestBody);
                
                String idStr = data.get("id");
                
                // Se não encontrou no body, tenta pegar da URL
                if (idStr == null) {
                    String path = exchange.getRequestURI().getPath();
                    String[] segments = path.split("/");
                    // path esperado: /api/accounts/123 -> ["", "api", "accounts", "123"]
                    if (segments.length > 3) {
                        idStr = segments[segments.length - 1];
                    }
                }
                
                if (idStr == null) throw new IllegalArgumentException("ID da conta é obrigatório");
                int accountId = Integer.parseInt(idStr);
                
                String name = data.get("name");
                if (name == null) name = data.get("nome");

                String type = data.get("type");
                if (type == null) type = data.get("tipo");
                
                String balanceStr = data.get("balance");
                if (balanceStr == null) {
                    balanceStr = data.get("saldoInicial");
                }
                if (balanceStr == null) throw new IllegalArgumentException("Saldo é obrigatório");
                double balance = parseDoubleBrazilian(balanceStr);
                
                // Lê campos opcionais de cartão de crédito
                Integer diaFechamento = null;
                Integer diaPagamento = null;
                String diaFechamentoStr = data.get("diaFechamento");
                if (diaFechamentoStr != null && !diaFechamentoStr.isEmpty()) {
                    diaFechamento = Integer.parseInt(diaFechamentoStr);
                }
                String diaPagamentoStr = data.get("diaPagamento");
                if (diaPagamentoStr != null && !diaPagamentoStr.isEmpty()) {
                    diaPagamento = Integer.parseInt(diaPagamentoStr);
                }
                
                // Busca userId da conta antes de atualizar para invalidar cache
                Conta conta = bancoDados.buscarConta(accountId);
                int userId = conta != null ? conta.getIdUsuario() : requireUserId(exchange);
                
                bancoDados.atualizarConta(accountId, name, type, balance, diaFechamento, diaPagamento);
                
                // Invalida cache de contas e overview
                invalidateCache("accounts_" + userId);
                invalidateCache("overview_" + userId);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Conta atualizada com sucesso");
                
                sendJsonResponse(exchange, 200, response);
            } catch (UnauthorizedException e) {
                handleUnauthorized(exchange, e);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                
                sendJsonResponse(exchange, 400, response);
            } finally {
                bancoDados.closeConnection();
            }
        }
        
        private void handleDeleteAccount(HttpExchange exchange) throws IOException {
            try {
                requireUserId(exchange);
                String idParam = getQueryParam(exchange, "id");
                
                // Se não encontrou no query param, tenta pegar da URL
                if (idParam == null) {
                    String path = exchange.getRequestURI().getPath();
                    String[] segments = path.split("/");
                    // path esperado: /api/accounts/123 -> ["", "api", "accounts", "123"]
                    if (segments.length > 3) {
                        idParam = segments[segments.length - 1];
                    }
                }
                
                if (idParam == null) {
                    sendErrorResponse(exchange, 400, "ID da conta não fornecido");
                    return;
                }
                
                int accountId = Integer.parseInt(idParam);
                
                // Busca userId da conta antes de excluir para invalidar cache
                Conta conta = bancoDados.buscarConta(accountId);
                int userId = conta != null ? conta.getIdUsuario() : requireUserId(exchange);
                
                bancoDados.excluirConta(accountId);
                
                // Invalida cache de contas e overview
                invalidateCache("accounts_" + userId);
                invalidateCache("overview_" + userId);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Conta excluída com sucesso");
                
                sendJsonResponse(exchange, 200, response);
            } catch (UnauthorizedException e) {
                handleUnauthorized(exchange, e);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                
                sendJsonResponse(exchange, 400, response);
            } finally {
                // Garante que conexão da thread seja fechada após requisição
                bancoDados.closeConnection();
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
                    if (receita.getObservacoes() != null && receita.getObservacoes().length > 0) {
                        List<String> observacoesList = new ArrayList<>();
                        for (String obs : receita.getObservacoes()) {
                            observacoesList.add(obs);
                        }
                        transaction.put("observacoes", observacoesList);
                    }
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
            } finally {
                // Garante que conexão da thread seja fechada após requisição
                bancoDados.closeConnection();
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
                
                // OTIMIZAÇÃO: Busca todas as categorias, tags e observações em batch (evita N+1 queries)
                List<Integer> idsGastos = new ArrayList<>();
                for (Gasto gasto : expenses) {
                    idsGastos.add(gasto.getIdGasto());
                }
                
                Map<Integer, List<Categoria>> categoriasPorGasto = bancoDados.buscarCategoriasDeGastos(idsGastos);
                Map<Integer, List<Tag>> tagsPorGasto = bancoDados.buscarTagsDeGastos(idsGastos);
                Map<Integer, String[]> observacoesPorGasto = bancoDados.buscarObservacoesDeGastos(idsGastos);
                
                List<Integer> idsReceitas = new ArrayList<>();
                for (Receita receita : incomes) {
                    idsReceitas.add(receita.getIdReceita());
                }
                
                Map<Integer, List<Tag>> tagsPorReceita = bancoDados.buscarTagsDeReceitas(idsReceitas);
                Map<Integer, String[]> observacoesPorReceita = bancoDados.buscarObservacoesDeReceitas(idsReceitas);
                
                List<Map<String, Object>> transactions = new ArrayList<>();
                
                // Add expenses
                for (Gasto gasto : expenses) {
                    // Busca categorias do mapa (já carregadas em batch)
                    List<Categoria> categorias = categoriasPorGasto.getOrDefault(gasto.getIdGasto(), new ArrayList<>());
                    List<String> nomesCategorias = new ArrayList<>();
                    List<Integer> idsCategorias = new ArrayList<>();
                    
                    for (Categoria cat : categorias) {
                        nomesCategorias.add(cat.getNome());
                        idsCategorias.add(cat.getIdCategoria());
                    }
                    
                    String categoriasStr = nomesCategorias.isEmpty() ? "Sem Categoria" : String.join(", ", nomesCategorias);
                    
                    // Busca tags do mapa (já carregadas em batch)
                    List<Tag> tags = tagsPorGasto.getOrDefault(gasto.getIdGasto(), new ArrayList<>());
                    List<Map<String, Object>> tagsList = new ArrayList<>();
                    for (Tag tag : tags) {
                        Map<String, Object> tagMap = new HashMap<>();
                        tagMap.put("idTag", tag.getIdTag());
                        tagMap.put("nome", tag.getNome());
                        tagMap.put("cor", tag.getCor());
                        tagsList.add(tagMap);
                    }
                    
                    // Busca observações do mapa (já carregadas em batch)
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
                
                // Add incomes (não são filtradas por categoria)
                for (Receita receita : incomes) {
                    // Busca tags do mapa (já carregadas em batch)
                    List<Tag> tags = tagsPorReceita.getOrDefault(receita.getIdReceita(), new ArrayList<>());
                    List<Map<String, Object>> tagsList = new ArrayList<>();
                    for (Tag tag : tags) {
                        Map<String, Object> tagMap = new HashMap<>();
                        tagMap.put("idTag", tag.getIdTag());
                        tagMap.put("nome", tag.getNome());
                        tagMap.put("cor", tag.getCor());
                        tagsList.add(tagMap);
                    }
                    
                    // Busca observações do mapa (já carregadas em batch)
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
                
                // Sort by date (most recent first)
                transactions.sort((a, b) -> ((String) b.get("date")).compareTo((String) a.get("date")));
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", transactions);
                
                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorResponse(exchange, 500, "Erro interno do servidor");
            } finally {
                bancoDados.closeConnection();
            }
        }
    }
    
    static class ExpensesHandler implements HttpHandler {
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
                    sendErrorResponse(exchange, 405, "Método não permitido: " + method);
                }
            } catch (UnauthorizedException e) {
                handleUnauthorized(exchange, e);
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
                int userId = requireUserId(exchange);
                String requestBody = readRequestBody(exchange);
                Map<String, Object> data = parseJsonWithNested(requestBody);
                data.put("userId", userId);
                
                String description = (String) data.get("description");
                double value = ((Number) data.get("value")).doubleValue();
                LocalDate date = LocalDate.parse((String) data.get("date"));
                int accountId = ((Number) data.get("accountId")).intValue();
                String frequency = (String) data.get("frequency");
                
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
                            // Trata quebras de linha literais que podem vir do JSON
                            observacoesStr = observacoesStr.replace("\\n", "\n");
                            
                            // Divide por quebras de linha apenas (removemos , e ; para permitir texto normal)
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
                
                // Valida se a conta não é do tipo "Investimento"
                Conta conta = bancoDados.buscarConta(accountId);
                if (conta == null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "Conta não encontrada");
                    sendJsonResponse(exchange, 400, response);
                    return;
                }
                // Verifica se é conta de investimento (aceita "Investimento" ou "Investimento (Corretora)")
                String tipoConta = conta.getTipo() != null ? conta.getTipo().toLowerCase().trim() : "";
                if (tipoConta.equals("investimento") || tipoConta.equals("investimento (corretora)") || tipoConta.startsWith("investimento")) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "Contas de investimento não podem ser usadas para gastos");
                    sendJsonResponse(exchange, 400, response);
                    return;
                }
                
                int expenseId = bancoDados.cadastrarGasto(description, value, date, frequency, userId, categoryIds, accountId, observacoes);
                
                // Associa tags se houver
                for (int tagId : tagIds) {
                    bancoDados.associarTagTransacao(expenseId, "GASTO", tagId);
                }
                
                // Invalida cache de overview e categorias
                invalidateCache("overview_" + userId);
                invalidateCache("categories_" + userId);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Gasto registrado com sucesso");
                response.put("expenseId", expenseId);
                
                sendJsonResponse(exchange, 201, response);
            } catch (UnauthorizedException e) {
                handleUnauthorized(exchange, e);
            } catch (Exception e) {
                e.printStackTrace();
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Erro: " + e.getMessage());
                sendJsonResponse(exchange, 400, response);
            } finally {
                // Garante que conexão da thread seja fechada após requisição
                bancoDados.closeConnection();
            }
        }
        
        private void handleDelete(HttpExchange exchange) throws IOException {
            try {
                requireUserId(exchange);
                String idParam = getQueryParam(exchange, "id");
                
                if (idParam == null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "ID do gasto não fornecido");
                    sendJsonResponse(exchange, 400, response);
                    return;
                }
                
                int expenseId = Integer.parseInt(idParam);
                
                // Busca userId do gasto antes de excluir para invalidar cache
                Gasto gasto = bancoDados.buscarGasto(expenseId);
                int userId = gasto != null ? gasto.getIdUsuario() : requireUserId(exchange);
                
                bancoDados.excluirGasto(expenseId);
                
                // Invalida cache de overview e categorias
                invalidateCache("overview_" + userId);
                invalidateCache("categories_" + userId);
                invalidateCache("totalExpense_" + userId);
                invalidateCache("balance_" + userId);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Gasto excluído com sucesso");
                
                sendJsonResponse(exchange, 200, response);
            } catch (UnauthorizedException e) {
                handleUnauthorized(exchange, e);
            } catch (Exception e) {
                e.printStackTrace();
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Erro: " + e.getMessage());
                sendJsonResponse(exchange, 400, response);
            } finally {
                // Garante que conexão da thread seja fechada após requisição
                bancoDados.closeConnection();
            }
        }
    }
    
    static class IncomesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod().toUpperCase();
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();
            
            // Debug log
            System.out.println("IncomesHandler - Method: " + method + ", Path: " + path + ", Query: " + query);
            
            if ("OPTIONS".equals(method)) {
                handleOptions(exchange);
            } else if ("POST".equals(method)) {
                handlePost(exchange);
            } else if ("DELETE".equals(method)) {
                handleDelete(exchange);
            } else {
                System.out.println("IncomesHandler - Método não permitido: " + method);
                sendErrorResponse(exchange, 405, "Método não permitido: " + method);
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
                int userId = requireUserId(exchange);
                String requestBody = readRequestBody(exchange);
                Map<String, Object> data = parseJsonWithNested(requestBody);
                data.put("userId", userId);
                
                String description = (String) data.getOrDefault("description", "Receita");
                double value = ((Number) data.get("value")).doubleValue();
                LocalDate date = LocalDate.parse((String) data.get("date"));
                int accountId = ((Number) data.get("accountId")).intValue();
                
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
                
                // Valida se a conta não é do tipo "Investimento"
                Conta conta = bancoDados.buscarConta(accountId);
                if (conta == null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "Conta não encontrada");
                    sendJsonResponse(exchange, 400, response);
                    return;
                }
                // Verifica se é conta de investimento (aceita "Investimento" ou "Investimento (Corretora)")
                String tipoConta = conta.getTipo() != null ? conta.getTipo().toLowerCase().trim() : "";
                if (tipoConta.equals("investimento") || tipoConta.equals("investimento (corretora)") || tipoConta.startsWith("investimento")) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "Contas de investimento não podem ser usadas para receitas");
                    sendJsonResponse(exchange, 400, response);
                    return;
                }
                
                int incomeId = bancoDados.cadastrarReceita(description, value, date, userId, accountId, observacoes);
                
                // Invalida cache de overview
                invalidateCache("overview_" + userId);
                invalidateCache("totalIncome_" + userId);
                invalidateCache("balance_" + userId);
                
                // Associa tags se houver
                for (int tagId : tagIds) {
                    bancoDados.associarTagTransacao(incomeId, "RECEITA", tagId);
                }
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Receita registrada com sucesso");
                response.put("incomeId", incomeId);
                
                sendJsonResponse(exchange, 201, response);
            } catch (UnauthorizedException e) {
                handleUnauthorized(exchange, e);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                
                sendJsonResponse(exchange, 400, response);
            } finally {
                // Garante que conexão da thread seja fechada após requisição
                bancoDados.closeConnection();
            }
        }
        
        private void handleDelete(HttpExchange exchange) throws IOException {
            try {
                requireUserId(exchange);
                String idParam = getQueryParam(exchange, "id");
                if (idParam == null || !idParam.matches("\\d+")) {
                    sendErrorResponse(exchange, 400, "ID da receita inválido");
                    return;
                }
                
                int incomeId = Integer.parseInt(idParam);
                // Busca userId da receita antes de excluir para invalidar cache
                Receita receita = bancoDados.buscarReceita(incomeId);
                int userId = receita != null ? receita.getIdUsuario() : requireUserId(exchange);
                
                bancoDados.excluirReceita(incomeId);
                
                // Invalida cache de overview
                invalidateCache("overview_" + userId);
                invalidateCache("totalIncome_" + userId);
                invalidateCache("balance_" + userId);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Receita excluída com sucesso");
                
                sendJsonResponse(exchange, 200, response);
            } catch (UnauthorizedException e) {
                handleUnauthorized(exchange, e);
            } catch (Exception e) {
                e.printStackTrace();
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Erro: " + e.getMessage());
                sendJsonResponse(exchange, 400, response);
            } finally {
                // Garante que conexão da thread seja fechada após requisição
                bancoDados.closeConnection();
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
                
                // Otimização: busca todas as categorias de uma vez em vez de uma por orçamento
                List<Categoria> allCategories = bancoDados.buscarCategoriasPorUsuario(userId);
                Map<Integer, Categoria> categoryMap = new HashMap<>();
                for (Categoria cat : allCategories) {
                    categoryMap.put(cat.getIdCategoria(), cat);
                }
                
                for (Orcamento orcamento : budgets) {
                    Categoria categoria = categoryMap.get(orcamento.getIdCategoria());
                    // Calcula apenas os gastos do usuário para esta categoria
                    double spent = bancoDados.calcularTotalGastosPorCategoriaEUsuario(orcamento.getIdCategoria(), userId);
                    double percentageUsed = orcamento.getValorPlanejado() > 0 ? 
                        (spent / orcamento.getValorPlanejado()) * 100 : 0;
                    
                    // Formata para 2 casas decimais
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
                
                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                e.printStackTrace(); // Log do erro para debug
                sendErrorResponse(exchange, 500, "Erro interno do servidor");
            } finally {
                // Garante que conexão da thread seja fechada após requisição
                bancoDados.closeConnection();
            }
        }
        
        private void handleCreateBudget(HttpExchange exchange) throws IOException {
            try {
                int userId = requireUserId(exchange);
                String requestBody = readRequestBody(exchange);
                Map<String, String> data = parseJson(requestBody);
                
                int categoryId = Integer.parseInt(data.get("categoryId"));
                
                String valueStr = data.get("value");
                if (valueStr == null) throw new IllegalArgumentException("Valor é obrigatório");
                double value = parseDoubleBrazilian(valueStr);
                
                String period = data.get("period");
                
                int budgetId = bancoDados.cadastrarOrcamento(value, period, categoryId, userId);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Orçamento criado com sucesso");
                response.put("budgetId", budgetId);
                
                sendJsonResponse(exchange, 201, response);
            } catch (UnauthorizedException e) {
                handleUnauthorized(exchange, e);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                
                sendJsonResponse(exchange, 400, response);
            } finally {
                bancoDados.closeConnection();
            }
        }
        
        private void handleUpdateBudget(HttpExchange exchange) throws IOException {
            try {
                requireUserId(exchange);
                String requestBody = readRequestBody(exchange);
                Map<String, String> data = parseJson(requestBody);
                
                int budgetId = Integer.parseInt(data.get("id"));
                
                String valueStr = data.get("value");
                if (valueStr == null) throw new IllegalArgumentException("Valor é obrigatório");
                double value = parseDoubleBrazilian(valueStr);
                
                String period = data.get("period");
                
                bancoDados.atualizarOrcamento(budgetId, value, period);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Orçamento atualizado com sucesso");
                
                sendJsonResponse(exchange, 200, response);
            } catch (UnauthorizedException e) {
                handleUnauthorized(exchange, e);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                
                sendJsonResponse(exchange, 400, response);
            } finally {
                // Garante que conexão da thread seja fechada após requisição
                bancoDados.closeConnection();
            }
        }
        
        private void handleDeleteBudget(HttpExchange exchange) throws IOException {
            try {
                String idParam = getQueryParam(exchange, "id");
                
                // Se não encontrou no query param, tenta pegar da URL
                if (idParam == null) {
                    String path = exchange.getRequestURI().getPath();
                    String[] segments = path.split("/");
                    // path esperado: /api/budgets/123 -> ["", "api", "budgets", "123"]
                    if (segments.length > 3) {
                        idParam = segments[segments.length - 1];
                    }
                }
                
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
            } finally {
                // Garante que conexão da thread seja fechada após requisição
                bancoDados.closeConnection();
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
                e.printStackTrace(); // Log do erro para debug
                sendErrorResponse(exchange, 500, "Erro interno do servidor");
            } finally {
                // Garante que conexão da thread seja fechada após requisição
                bancoDados.closeConnection();
            }
        }
        
        private void handleCreateTag(HttpExchange exchange) throws IOException {
            try {
                int userId = requireUserId(exchange);
                String requestBody = readRequestBody(exchange);
                Map<String, String> data = parseJson(requestBody);
                
                String nome = data.get("nome");
                String cor = data.get("cor");
                if (cor == null || cor.isEmpty()) {
                    cor = "#6B7280"; // Cor padrão cinza
                }
                int tagId = bancoDados.cadastrarTag(nome, cor, userId);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Tag criada com sucesso");
                response.put("tagId", tagId);
                
                sendJsonResponse(exchange, 201, response);
            } catch (UnauthorizedException e) {
                handleUnauthorized(exchange, e);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                
                sendJsonResponse(exchange, 400, response);
            } finally {
                // Garante que conexão da thread seja fechada após requisição
                bancoDados.closeConnection();
            }
        }
        
        private void handleUpdateTag(HttpExchange exchange) throws IOException {
            try {
                requireUserId(exchange);
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
            } catch (UnauthorizedException e) {
                handleUnauthorized(exchange, e);
            } catch (Exception e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", e.getMessage());
                
                sendJsonResponse(exchange, 400, response);
            } finally {
                // Garante que conexão da thread seja fechada após requisição
                bancoDados.closeConnection();
            }
        }
        
        private void handleDeleteTag(HttpExchange exchange) throws IOException {
            try {
                String idParam = getQueryParam(exchange, "id");
                
                // Se não encontrou no query param, tenta pegar da URL
                if (idParam == null) {
                    String path = exchange.getRequestURI().getPath();
                    String[] segments = path.split("/");
                    // path esperado: /api/tags/123 -> ["", "api", "tags", "123"]
                    if (segments.length > 3) {
                        idParam = segments[segments.length - 1];
                    }
                }
                
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
            } finally {
                // Garante que conexão da thread seja fechada após requisição
                bancoDados.closeConnection();
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
                int userId = requireUserId(exchange);
                String requestBody = readRequestBody(exchange);
                Map<String, String> data = parseJson(requestBody);
                
                String format = data.get("format"); // "csv" ou "xlsx"
                String period = data.get("period");
                String startDateParam = data.get("startDate");
                String endDateParam = data.get("endDate");
                
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
                
            } catch (UnauthorizedException e) {
                handleUnauthorized(exchange, e);
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
                    double currentPrice = 0.0;
                    double currentValue = 0.0;
                    
                    // Converte valor do aporte para BRL se o investimento foi registrado em outra moeda
                    double valorAporteBRL = inv.getValorAporte();
                    double precoAporteBRL = inv.getPrecoAporte();
                    
                    if (!"BRL".equals(inv.getMoeda())) {
                        double exchangeRate = quoteService.getExchangeRate(inv.getMoeda(), "BRL");
                        valorAporteBRL *= exchangeRate;
                        precoAporteBRL *= exchangeRate;
                    }
                    
                    // Para renda fixa, calcula valor atual baseado nos índices
                    if ("RENDA_FIXA".equals(inv.getCategoria())) {
                        currentValue = quoteService.calculateFixedIncomeValue(
                            valorAporteBRL,
                            inv.getTipoInvestimento(),
                            inv.getTipoRentabilidade(),
                            inv.getIndice(),
                            inv.getPercentualIndice(),
                            inv.getTaxaFixa(),
                            inv.getDataAporte(),
                            inv.getDataVencimento(),
                            LocalDate.now()
                        );
                        currentPrice = currentValue; // Para renda fixa, preço = valor atual
                    } else {
                        // Para outros tipos, busca cotação atual
                        QuoteService.QuoteResult quote = quoteService.getQuote(inv.getNome(), inv.getCategoria(), null);
                        currentPrice = quote != null && quote.success ? quote.price : inv.getPrecoAporte();
                        
                        // Converte preço atual para BRL se a cotação vier em outra moeda
                        if (quote != null && quote.success && !"BRL".equals(quote.currency)) {
                            double exchangeRate = quoteService.getExchangeRate(quote.currency, "BRL");
                            currentPrice *= exchangeRate;
                        }
                        
                        currentValue = inv.getQuantidade() * currentPrice;
                    }
                    
                    double returnValue = currentValue - valorAporteBRL;
                    double returnPercent = valorAporteBRL > 0 ? (returnValue / valorAporteBRL) * 100 : 0;
                    
                    totalInvested += valorAporteBRL;
                    totalCurrent += currentValue;
                    
                    Map<String, Object> invData = new HashMap<>();
                    invData.put("idInvestimento", inv.getIdInvestimento());
                    invData.put("nome", inv.getNome());
                    invData.put("nomeAtivo", inv.getNomeAtivo());
                    invData.put("categoria", inv.getCategoria());
                    invData.put("quantidade", inv.getQuantidade());
                    invData.put("precoAporte", precoAporteBRL);
                    invData.put("valorAporte", valorAporteBRL);
                    invData.put("corretagem", inv.getCorretagem());
                    invData.put("corretora", inv.getCorretora());
                    invData.put("dataAporte", inv.getDataAporte().toString());
                    invData.put("moeda", inv.getMoeda());
                    invData.put("precoAtual", currentPrice);
                    invData.put("valorAtual", currentValue);
                    invData.put("retorno", returnValue);
                    invData.put("retornoPercent", returnPercent);
                    
                    // Campos específicos de renda fixa
                    if ("RENDA_FIXA".equals(inv.getCategoria())) {
                        invData.put("tipoInvestimento", inv.getTipoInvestimento());
                        invData.put("tipoRentabilidade", inv.getTipoRentabilidade());
                        invData.put("indice", inv.getIndice());
                        invData.put("percentualIndice", inv.getPercentualIndice());
                        invData.put("taxaFixa", inv.getTaxaFixa());
                        if (inv.getDataVencimento() != null) {
                            invData.put("dataVencimento", inv.getDataVencimento().toString());
                        }
                    }
                    
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
            } catch (UnauthorizedException e) {
                handleUnauthorized(exchange, e);
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorResponse(exchange, 500, "Erro interno do servidor: " + e.getMessage());
            }
        }
        
        private void handleCreateInvestment(HttpExchange exchange) throws IOException {
            try {
                int userId = requireUserId(exchange);
                String requestBody = readRequestBody(exchange);
                Map<String, Object> data = parseJsonWithNested(requestBody);
                data.put("userId", userId);
                
                String nome = (String) data.get("nome");
                String categoria = (String) data.get("categoria");
                double quantidade = ((Number) data.get("quantidade")).doubleValue();
                double corretagem = data.containsKey("corretagem") ? 
                    ((Number) data.get("corretagem")).doubleValue() : 0.0;
                
                // Valida quantidade (deve ser diferente de zero, permite negativos para vendas)
                if (quantidade == 0 || Double.isNaN(quantidade) || Double.isInfinite(quantidade)) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "A quantidade deve ser diferente de zero");
                    sendJsonResponse(exchange, 400, response);
                    return;
                }
                String dataAporteStr = (String) data.get("dataAporte");
                LocalDate dataAporte = dataAporteStr != null ? LocalDate.parse(dataAporteStr) : LocalDate.now();
                
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
                
                // Aceita tanto "Investimento" quanto "Investimento (Corretora)"
                String tipoConta = conta.getTipo().toLowerCase().trim();
                if (!tipoConta.equals("investimento") && !tipoConta.equals("investimento (corretora)") && !tipoConta.startsWith("investimento")) {
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
                
                // Processa campos específicos de renda fixa
                String tipoInvestimento = null;
                String tipoRentabilidade = null;
                String indice = null;
                Double percentualIndice = null;
                Double taxaFixa = null;
                LocalDate dataVencimento = null;
                
                if ("RENDA_FIXA".equals(categoria)) {
                    tipoInvestimento = (String) data.get("tipoInvestimento");
                    // Para renda fixa, o valorAporte vem diretamente
                    if (data.containsKey("valorAporte")) {
                        double valorAporte = ((Number) data.get("valorAporte")).doubleValue();
                        precoAporte = valorAporte; // Para renda fixa, precoAporte = valorAporte
                        quantidade = 1.0; // Quantidade sempre 1 para renda fixa
                    }
                    
                    tipoRentabilidade = (String) data.get("tipoRentabilidade");
                    indice = data.containsKey("indice") ? (String) data.get("indice") : null;
                    
                    if (data.containsKey("percentualIndice")) {
                        Object percentObj = data.get("percentualIndice");
                        if (percentObj != null) {
                            if (percentObj instanceof Number) {
                                percentualIndice = ((Number) percentObj).doubleValue();
                            } else if (percentObj instanceof String) {
                                String percentStr = ((String) percentObj).trim();
                                if (!percentStr.isEmpty() && !percentStr.equalsIgnoreCase("null")) {
                                    try {
                                        percentualIndice = parseDoubleBrazilian(percentStr);
                                    } catch (NumberFormatException e) {
                                        // Ignora valores inválidos
                                    }
                                }
                            }
                        }
                    }
                    
                    if (data.containsKey("taxaFixa")) {
                        Object taxaObj = data.get("taxaFixa");
                        if (taxaObj != null) {
                            if (taxaObj instanceof Number) {
                                taxaFixa = ((Number) taxaObj).doubleValue();
                            } else if (taxaObj instanceof String) {
                                String taxaStr = ((String) taxaObj).trim();
                                if (!taxaStr.isEmpty() && !taxaStr.equalsIgnoreCase("null")) {
                                    try {
                                        taxaFixa = parseDoubleBrazilian(taxaStr);
                                    } catch (NumberFormatException e) {
                                        // Ignora valores inválidos
                                    }
                                }
                            }
                        }
                    }
                    
                    if (data.containsKey("dataVencimento")) {
                        String dataVencimentoStr = (String) data.get("dataVencimento");
                        if (dataVencimentoStr != null) {
                            dataVencimento = LocalDate.parse(dataVencimentoStr);
                        }
                    }
                } else {
                // Valida preço para outros tipos
                // Preço de aporte deve ser positivo mesmo na venda (é o preço unitário)
                if (precoAporte <= 0) {
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", false);
                        response.put("message", "O preço de aporte deve ser maior que zero");
                        sendJsonResponse(exchange, 400, response);
                        return;
                    }
                }
                
                int investmentId = bancoDados.cadastrarInvestimento(nome, nomeAtivo, categoria, quantidade, 
                                                                  precoAporte, corretagem, corretoraFinal,
                                                                  dataAporte, userId, accountId, moeda,
                                                                  tipoInvestimento, tipoRentabilidade, indice, percentualIndice, taxaFixa, dataVencimento);
                
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
                String nomeAtivo = data.containsKey("nomeAtivo") ? (String) data.get("nomeAtivo") : null;
                String categoria = (String) data.get("categoria");
                double quantidade = ((Number) data.get("quantidade")).doubleValue();
                double corretagem = ((Number) data.get("corretagem")).doubleValue();
                
                // Valida quantidade (deve ser diferente de zero, permite negativos para vendas)
                if (quantidade == 0 || Double.isNaN(quantidade) || Double.isInfinite(quantidade)) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "A quantidade deve ser diferente de zero");
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
                        // Se não havia nomeAtivo e a cotação retornou um, usa ele
                        if (nomeAtivo == null && quote.assetName != null) {
                            nomeAtivo = quote.assetName;
                        }
                    }
                }
                
                bancoDados.atualizarInvestimento(idInvestimento, nome, nomeAtivo, categoria, quantidade,
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
    
    static class InvestmentEvolutionHandler implements HttpHandler {
        private static final DateTimeFormatter LABEL_FORMATTER = DateTimeFormatter.ofPattern("dd/MM");
        private static final DateTimeFormatter LABEL_FORMATTER_WITH_YEAR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        private static final DateTimeFormatter HOURLY_LABEL_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
        private static final int MAX_DAYS = 3650; // ~10 anos

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "Método não permitido");
                return;
            }

            try {
                String userIdParam = getQueryParam(exchange, "userId");
                int userId = userIdParam != null ? Integer.parseInt(userIdParam) : 1;

                LocalDate today = LocalDate.now();
                LocalDate endDate = parseDateOrDefault(getQueryParam(exchange, "endDate"), today);

                // Busca investimentos antes de determinar o período final
                List<Investimento> transactions = bancoDados.buscarInvestimentosPorUsuario(userId);

                String periodParam = getQueryParam(exchange, "period");
                boolean twoHourResolution = "1D".equalsIgnoreCase(periodParam);

                LocalDate startDate = resolveStartDate(
                    getQueryParam(exchange, "startDate"),
                    periodParam,
                    endDate,
                    transactions
                );

                if (startDate.isAfter(endDate)) {
                    LocalDate tmp = startDate;
                    startDate = endDate;
                    endDate = tmp;
                }

                long totalDays = ChronoUnit.DAYS.between(startDate, endDate);
                if (totalDays > MAX_DAYS) {
                    sendErrorResponse(exchange, 400, "Período máximo de 10 anos excedido. Reduza o intervalo.");
                    return;
                }

                // Otimização: reduz granularidade para períodos longos
                // Limita número máximo de pontos para evitar sobrecarga
                int maxPoints = 200; // Reduzido para 200 pontos para carregar mais rápido na primeira vez
                int dayStep = 1;
                if (totalDays > maxPoints) {
                    dayStep = (int) Math.ceil((double) totalDays / maxPoints);
                    // Ajusta para valores "redondos"
                    if (dayStep <= 2) dayStep = 1;
                    else if (dayStep <= 5) dayStep = 3;
                    else if (dayStep <= 10) dayStep = 7;
                    else if (dayStep <= 20) dayStep = 14; // 2 semanas
                    else if (dayStep <= 30) dayStep = 30; // 1 mês
                    else dayStep = 60; // 2 meses para períodos extremamente longos
                } else if (totalDays > 730) { // > 2 anos
                    dayStep = 7; // Semanal
                } else if (totalDays > 180) { // > 6 meses
                    dayStep = 3; // A cada 3 dias
                }

                // Determina se deve mostrar o ano (período > 1 ano)
                boolean showYear = totalDays > 365;
                Map<String, Object> data = buildEvolutionSeries(transactions, startDate, endDate, twoHourResolution, dayStep, showYear);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", data);

                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                e.printStackTrace();
                sendErrorResponse(exchange, 500, "Erro ao calcular evolução: " + e.getMessage());
            } finally {
                bancoDados.closeConnection();
            }
        }

        private LocalDate parseDateOrDefault(String value, LocalDate fallback) {
            if (value == null || value.isEmpty()) {
                return fallback;
            }
            return LocalDate.parse(value);
        }

        private LocalDate resolveStartDate(String startParam, String periodParam, LocalDate endDate, List<Investimento> transactions) {
            if (startParam != null && !startParam.isEmpty()) {
                return LocalDate.parse(startParam);
            }

            if (periodParam != null) {
                switch (periodParam.toUpperCase()) {
                    case "1D":
                        return endDate.minusDays(1);
                    case "1W":
                        return endDate.minusWeeks(1);
                    case "1M":
                        return endDate.minusMonths(1);
                    case "6M":
                        return endDate.minusMonths(6);
                    case "YTD":
                        return LocalDate.of(endDate.getYear(), 1, 1);
                    case "1Y":
                        return endDate.minusYears(1);
                    case "5Y":
                        return endDate.minusYears(5);
                    case "ALL":
                        return transactions.stream()
                            .map(Investimento::getDataAporte)
                            .min(LocalDate::compareTo)
                            .orElse(endDate.minusMonths(1));
                    default:
                        break;
                }
            }

            return endDate.minusMonths(1);
        }

        private Map<String, Object> buildEvolutionSeries(List<Investimento> transactions, LocalDate startDate, LocalDate endDate, boolean useTwoHourSteps) {
            return buildEvolutionSeries(transactions, startDate, endDate, useTwoHourSteps, 1, false);
        }
        
        private Map<String, Object> buildEvolutionSeries(List<Investimento> transactions, LocalDate startDate, LocalDate endDate, boolean useTwoHourSteps, int dayStep) {
            return buildEvolutionSeries(transactions, startDate, endDate, useTwoHourSteps, dayStep, false);
        }
        
        private Map<String, Object> buildEvolutionSeries(List<Investimento> transactions, LocalDate startDate, LocalDate endDate, boolean useTwoHourSteps, int dayStep, boolean showYear) {
            Map<String, Object> data = new HashMap<>();
            List<String> labels = new ArrayList<>();
            List<Double> investedPoints = new ArrayList<>();
            List<Double> currentPoints = new ArrayList<>();
            
            // Mapas para rastrear valores por categoria
            Map<String, List<Double>> categoryInvested = new HashMap<>();
            Map<String, List<Double>> categoryCurrent = new HashMap<>();
            // Set para rastrear todas as categorias que já apareceram (para garantir continuidade)
            Set<String> allCategoriesSeen = new HashSet<>();

            data.put("labels", labels);
            data.put("invested", investedPoints);
            data.put("current", currentPoints);
            data.put("startDate", startDate.toString());
            data.put("endDate", endDate.toString());
            data.put("resolution", useTwoHourSteps ? "2h" : "1d");

            if (transactions == null || transactions.isEmpty()) {
                data.put("points", 0);
                return data;
            }

            transactions.sort(Comparator.comparing(Investimento::getDataAporte));

            QuoteService quoteService = QuoteService.getInstance();
            Map<String, AssetState> assetState = new LinkedHashMap<>();
            Map<String, Double> priceCache = new HashMap<>();
            
            // Otimização: para períodos muito longos, reduz busca de preços
            long totalDays = ChronoUnit.DAYS.between(startDate, endDate);
            int priceLookupInterval = 1; // Busca preço a cada N dias (padrão: diário)
            if (totalDays > 730) { // > 2 anos
                priceLookupInterval = 14; // Busca preço a cada 2 semanas para períodos muito longos
            } else if (totalDays > 365) { // > 1 ano
                priceLookupInterval = 7; // Busca preço semanalmente
            } else if (totalDays > 180) { // > 6 meses
                priceLookupInterval = 3; // Busca preço a cada 3 dias
            }

            int txIndex = 0;
            
            // OTIMIZAÇÃO CRÍTICA: Pre-busca cotações em batch para períodos grandes
            // Processa transações primeiro para identificar ativos, depois busca cotações
            if (totalDays > 365) {
                // Processa todas as transações até a data final para identificar todos os ativos
                Map<String, AssetState> tempAssetState = new LinkedHashMap<>();
                int tempTxIndex = 0;
                for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                    tempTxIndex = consumeTransactions(transactions, tempAssetState, tempTxIndex, date);
                }
                // Agora pre-busca cotações baseado nos ativos identificados
                preFetchQuotesInBatch(tempAssetState, startDate, endDate, priceLookupInterval, quoteService, priceCache);
            }

            if (useTwoHourSteps) {
                LocalDateTime cursor = startDate.atStartOfDay();
                LocalDateTime endCursor = endDate.atStartOfDay();
                if (endCursor.isBefore(cursor)) {
                    endCursor = cursor;
                }

                while (!cursor.isAfter(endCursor)) {
                    LocalDate currentDate = cursor.toLocalDate();
                    txIndex = consumeTransactions(transactions, assetState, txIndex, currentDate);
                    accumulatePoint(assetState, quoteService, priceCache, labels, investedPoints, currentPoints,
                        categoryInvested, categoryCurrent, allCategoriesSeen, cursor.format(HOURLY_LABEL_FORMATTER), currentDate, cursor, 1);
                    cursor = cursor.plusHours(2);
                }
            } else {
                // Usa step passado como parâmetro (já calculado no handler)
                // Se não foi passado, calcula baseado no período
                if (dayStep <= 0) {
                    dayStep = 1; // Padrão: diário
                    if (totalDays > 730) { // > 2 anos
                        dayStep = 7; // Semanal
                    } else if (totalDays > 180) { // > 6 meses
                        dayStep = 3; // A cada 3 dias
                    }
                }
                
                DateTimeFormatter labelFormatter = showYear ? LABEL_FORMATTER_WITH_YEAR : LABEL_FORMATTER;
                for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(dayStep)) {
                    txIndex = consumeTransactions(transactions, assetState, txIndex, date);
                    accumulatePoint(assetState, quoteService, priceCache, labels, investedPoints, currentPoints,
                        categoryInvested, categoryCurrent, allCategoriesSeen, date.format(labelFormatter), date, date.atStartOfDay(), priceLookupInterval);
                }
            }

            // Adiciona dados por categoria ao response
            // Garante que todas as categorias tenham o mesmo número de pontos que os labels
            int expectedSize = labels.size();
            Map<String, Map<String, List<Double>>> categoriesData = new HashMap<>();
            for (String category : allCategoriesSeen) {
                List<Double> investedList = categoryInvested.getOrDefault(category, new ArrayList<>());
                List<Double> currentList = categoryCurrent.getOrDefault(category, new ArrayList<>());
                
                // Preenche com zeros se a lista estiver menor que o esperado
                while (investedList.size() < expectedSize) {
                    investedList.add(0.0);
                }
                while (currentList.size() < expectedSize) {
                    currentList.add(0.0);
                }
                
                Map<String, List<Double>> catData = new HashMap<>();
                catData.put("invested", investedList);
                catData.put("current", currentList);
                categoriesData.put(category, catData);
            }
            data.put("categories", categoriesData);

            data.put("points", labels.size());
            return data;
        }

        private int consumeTransactions(List<Investimento> transactions, Map<String, AssetState> assetState, int txIndex, LocalDate cutoffDate) {
            while (txIndex < transactions.size() && !transactions.get(txIndex).getDataAporte().isAfter(cutoffDate)) {
                Investimento inv = transactions.get(txIndex);
                
                // IMPORTANTE: Ignora transações inválidas (quantidade zero)
                double qty = inv.getQuantidade();
                if (qty == 0.0 || Double.isNaN(qty) || Double.isInfinite(qty)) {
                    txIndex++;
                    continue;
                }
                
                String key = buildAssetKey(inv);
                AssetState state = assetState.computeIfAbsent(key, k -> AssetState.fromInvestment(inv));
                state.updateMetadata(inv);
                state.applyTransaction(inv);

                // IMPORTANTE: NÃO remove estados vazios durante o processamento do gráfico
                // Isso pode causar problemas se houver transações futuras que recriam o estado
                // O estado será naturalmente ignorado em accumulatePoint se estiver vazio
                // Mas manter no map permite que seja recriado por transações futuras
                // if (state.isEmpty()) {
                //     assetState.remove(key);
                // }
                txIndex++;
            }
            return txIndex;
        }

        private void accumulatePoint(
            Map<String, AssetState> assetState,
            QuoteService quoteService,
            Map<String, Double> priceCache,
            List<String> labels,
            List<Double> investedPoints,
            List<Double> currentPoints,
            String label,
            LocalDate dateForPricing,
            LocalDateTime dateTimeForPricing
        ) {
            accumulatePoint(assetState, quoteService, priceCache, labels, investedPoints, currentPoints,
                new HashMap<>(), new HashMap<>(), new HashSet<>(), label, dateForPricing, dateTimeForPricing, 1);
        }
        
        private void accumulatePoint(
            Map<String, AssetState> assetState,
            QuoteService quoteService,
            Map<String, Double> priceCache,
            List<String> labels,
            List<Double> investedPoints,
            List<Double> currentPoints,
            Map<String, List<Double>> categoryInvested,
            Map<String, List<Double>> categoryCurrent,
            Set<String> allCategoriesSeen,
            String label,
            LocalDate dateForPricing,
            LocalDateTime dateTimeForPricing,
            int priceLookupInterval
        ) {
            double totalInvested = 0;
            double totalCurrent = 0;
            
            // Mapas para rastrear valores por categoria
            Map<String, Double> categoryInvestedMap = new HashMap<>();
            Map<String, Double> categoryCurrentMap = new HashMap<>();

            // IMPORTANTE: Não remove estados vazios durante accumulatePoint
            // Isso pode causar problemas se houver transações futuras que recriam o estado
            // Em vez disso, apenas ignora estados vazios no cálculo, mas mantém no map
            Iterator<Map.Entry<String, AssetState>> iterator = assetState.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, AssetState> entry = iterator.next();
                AssetState state = entry.getValue();
                
                // Verifica se o estado está vazio ANTES de processar
                // Se estiver vazio, não calcula valores mas mantém a categoria rastreada
                boolean isEmpty = state.isEmpty();
                
                String category = state.category != null ? state.category : "OUTROS";
                // IMPORTANTE: Marca a categoria como vista ANTES de calcular valores
                // Isso garante que mesmo se houver algum problema no cálculo, a categoria será rastreada
                allCategoriesSeen.add(category);
                
                // Se o estado está vazio, pula o cálculo mas mantém a categoria rastreada
                if (isEmpty) {
                    continue;
                }
                
                double invested = state.getTotalCostBasis();
                totalInvested += invested;
                
                // Otimização: para períodos longos, reutiliza preço de dias próximos
                // Isso reduz drasticamente o número de requisições à API
                LocalDate priceLookupDate = dateForPricing;
                if (priceLookupInterval > 1) {
                    // Arredonda para o dia mais próximo que é múltiplo do intervalo
                    // Exemplo: se intervalo=7, busca preço apenas às segundas-feiras, reutiliza para outros dias
                    long daysSinceEpoch = dateForPricing.toEpochDay();
                    long roundedDays = (daysSinceEpoch / priceLookupInterval) * priceLookupInterval;
                    priceLookupDate = LocalDate.ofEpochDay(roundedDays);
                    // Se arredondou para frente, volta um intervalo
                    if (priceLookupDate.isAfter(dateForPricing)) {
                        priceLookupDate = priceLookupDate.minusDays(priceLookupInterval);
                    }
                }
                
                double price = resolvePriceForDate(state, priceLookupDate, 
                    priceLookupDate.equals(dateForPricing) ? dateTimeForPricing : null, 
                    quoteService, priceCache);
                
                // Para períodos muito longos, usa interpolação linear entre pontos conhecidos
                if (priceLookupInterval > 1 && !priceLookupDate.equals(dateForPricing)) {
                    // Tenta encontrar preços próximos para interpolar
                    LocalDate prevDate = priceLookupDate;
                    LocalDate nextDate = priceLookupDate.plusDays(priceLookupInterval);
                    
                    String prevKey = state.symbol + "|" + state.category + "|" + prevDate.toString();
                    String nextKey = state.symbol + "|" + state.category + "|" + nextDate.toString();
                    
                    Double prevPrice = priceCache.get(prevKey);
                    Double nextPrice = priceCache.get(nextKey);
                    
                    if (prevPrice != null && nextPrice != null) {
                        // Interpolação linear
                        long daysBetween = ChronoUnit.DAYS.between(prevDate, nextDate);
                        long daysFromPrev = ChronoUnit.DAYS.between(prevDate, dateForPricing);
                        if (daysBetween > 0) {
                            double ratio = (double) daysFromPrev / daysBetween;
                            price = prevPrice + (nextPrice - prevPrice) * ratio;
                        }
                    } else if (prevPrice != null) {
                        price = prevPrice; // Usa o preço anterior se não tiver próximo
                    }
                }
                
                double current = state.getTotalQuantity() * price;
                totalCurrent += current;
                
                // Acumula valores por categoria
                categoryInvestedMap.put(category, categoryInvestedMap.getOrDefault(category, 0.0) + invested);
                categoryCurrentMap.put(category, categoryCurrentMap.getOrDefault(category, 0.0) + current);
            }

            labels.add(label);
            investedPoints.add(roundMoney(totalInvested));
            currentPoints.add(roundMoney(totalCurrent));
            
            // Adiciona valores por categoria
            // Processa TODAS as categorias que já foram vistas, garantindo uma entrada por categoria por ponto
            for (String category : allCategoriesSeen) {
                if (categoryInvestedMap.containsKey(category)) {
                    // Categoria tem ativos neste ponto
                    categoryInvested.computeIfAbsent(category, k -> new ArrayList<>()).add(roundMoney(categoryInvestedMap.get(category)));
                    categoryCurrent.computeIfAbsent(category, k -> new ArrayList<>()).add(roundMoney(categoryCurrentMap.get(category)));
                } else {
                    // Categoria não tem mais ativos neste ponto, adiciona zero para manter continuidade
                    categoryInvested.computeIfAbsent(category, k -> new ArrayList<>()).add(0.0);
                    categoryCurrent.computeIfAbsent(category, k -> new ArrayList<>()).add(0.0);
                }
            }
            
            // IMPORTANTE: Processa categorias que apareceram pela primeira vez neste ponto
            // (não estavam em allCategoriesSeen antes)
            for (String category : categoryInvestedMap.keySet()) {
                if (!allCategoriesSeen.contains(category)) {
                    // Nova categoria que apareceu pela primeira vez
                    allCategoriesSeen.add(category);
                    // Preenche com zeros para pontos anteriores
                    List<Double> investedList = categoryInvested.computeIfAbsent(category, k -> new ArrayList<>());
                    List<Double> currentList = categoryCurrent.computeIfAbsent(category, k -> new ArrayList<>());
                    while (investedList.size() < labels.size() - 1) {
                        investedList.add(0.0);
                    }
                    while (currentList.size() < labels.size() - 1) {
                        currentList.add(0.0);
                    }
                    // Adiciona o valor atual
                    investedList.add(roundMoney(categoryInvestedMap.get(category)));
                    currentList.add(roundMoney(categoryCurrentMap.get(category)));
                }
            }
        }

        private String buildAssetKey(Investimento inv) {
            String category = inv.getCategoria() != null ? inv.getCategoria() : "OUTROS";
            String symbol = inv.getNome() != null ? inv.getNome() : "DESCONHECIDO";

            if ("RENDA_FIXA".equalsIgnoreCase(category)) {
                return category + "_" + inv.getIdInvestimento();
            }

            return category + "_" + symbol;
        }

        private double resolvePriceForDate(AssetState state, LocalDate date, QuoteService quoteService, Map<String, Double> priceCache) {
            return resolvePriceForDate(state, date, null, quoteService, priceCache);
        }
        
        private double resolvePriceForDate(AssetState state, LocalDate date, LocalDateTime dateTime, QuoteService quoteService, Map<String, Double> priceCache) {
            if ("RENDA_FIXA".equalsIgnoreCase(state.category)) {
                double totalValue = 0.0;
                for (PositionLayer layer : state.layers) {
                    totalValue += quoteService.calculateFixedIncomeValue(
                        layer.originalAmount,
                        state.tipoInvestimento,
                        state.tipoRentabilidade,
                        state.indice,
                        state.percentualIndice,
                        state.taxaFixa,
                        layer.aporteDate,
                        state.dataVencimento,
                        date
                    );
                }
                double qty = state.getTotalQuantity();
                double price = qty > 0 ? totalValue / qty : 0.0;
                if (price <= 0) {
                    price = state.lastKnownPrice > 0 ? state.lastKnownPrice : state.getAverageCost();
                }
                state.lastKnownPrice = price;
                return price;
            }

            // IMPORTANTE: Para datas futuras, usa sempre a última cotação conhecida
            LocalDate today = LocalDate.now();
            boolean isFuture = date != null && date.isAfter(today);
            
            String cacheKey = state.symbol + "|" + state.category + "|" + (dateTime != null ? dateTime.toString() : date.toString());
            if (priceCache.containsKey(cacheKey)) {
                return priceCache.get(cacheKey);
            }

            double price = 0.0;
            
            // Para datas futuras, tenta buscar a cotação atual primeiro (mais recente disponível)
            if (isFuture) {
                // Busca cotação atual para usar como referência para datas futuras
                QuoteService.QuoteResult currentQuote = quoteService.getQuote(state.symbol, state.category, null, null);
                if (currentQuote != null && currentQuote.success && currentQuote.price > 0) {
                    price = currentQuote.price;
                    String currency = currentQuote.currency != null ? currentQuote.currency : state.currency;
                    if (currency != null && !"BRL".equalsIgnoreCase(currency)) {
                        double exchangeRate = quoteService.getExchangeRate(currency, "BRL");
                        price *= exchangeRate;
                    }
                }
            } else {
                // Para datas passadas ou atuais, busca cotação normalmente
                QuoteService.QuoteResult quote = quoteService.getQuote(state.symbol, state.category, date, dateTime);
                if (quote != null && quote.success && quote.price > 0) {
                    price = quote.price;
                    String currency = quote.currency != null ? quote.currency : state.currency;
                    if (currency != null && !"BRL".equalsIgnoreCase(currency)) {
                        double exchangeRate = quoteService.getExchangeRate(currency, "BRL");
                        price *= exchangeRate;
                    }
                }
            }

            // Se não conseguiu cotação, usa fallback
            if (price <= 0) {
                if (isFuture && state.lastKnownPrice > 0) {
                    // Para datas futuras, usa a última cotação conhecida
                    price = state.lastKnownPrice;
                } else {
                    // Para datas passadas sem cotação, usa última conhecida ou preço médio
                    price = state.lastKnownPrice > 0 ? state.lastKnownPrice : state.getAverageCost();
                }
            }

            // IMPORTANTE: Atualiza lastKnownPrice apenas se conseguiu uma cotação válida
            // Para datas futuras, mantém a última cotação conhecida atualizada
            if (price > 0) {
                if (!isFuture) {
                    // Para datas passadas/atuais, atualiza lastKnownPrice
                    state.lastKnownPrice = price;
                } else if (state.lastKnownPrice <= 0) {
                    // Se é futura e não tinha última cotação, atualiza com a cotação atual
                    state.lastKnownPrice = price;
                }
            }
            
            priceCache.put(cacheKey, price);
            return price;
        }

        private double roundMoney(double value) {
            return Math.round(value * 100.0) / 100.0;
        }
        
        /**
         * Pre-busca cotações em batch para otimizar carregamento de gráficos grandes
         * Busca apenas as cotações necessárias baseado no intervalo de preços
         */
        private void preFetchQuotesInBatch(
            Map<String, AssetState> assetState,
            LocalDate startDate,
            LocalDate endDate,
            int priceLookupInterval,
            QuoteService quoteService,
            Map<String, Double> priceCache
        ) {
            // Para cada ativo único, busca cotações apenas nos intervalos necessários
            Set<String> processedAssets = new HashSet<>();
            for (AssetState state : assetState.values()) {
                if (state.isEmpty() || "RENDA_FIXA".equalsIgnoreCase(state.category)) {
                    continue; // Pula renda fixa (não precisa de cotação externa)
                }
                
                String symbol = state.symbol;
                String category = state.category != null ? state.category : "OUTROS";
                
                // Evita processar o mesmo ativo múltiplas vezes
                String assetKey = category + "_" + symbol;
                if (processedAssets.contains(assetKey)) {
                    continue;
                }
                processedAssets.add(assetKey);
                
                // Busca cotações apenas nos pontos de intervalo
                int fetched = 0;
                int maxFetches = 100; // Limita requisições por ativo
                for (LocalDate date = startDate; !date.isAfter(endDate) && fetched < maxFetches; date = date.plusDays(priceLookupInterval)) {
                    String cacheKey = symbol + "|" + category + "|" + date.toString();
                    if (!priceCache.containsKey(cacheKey)) {
                        // Busca a cotação e armazena no cache
                        QuoteService.QuoteResult quote = quoteService.getQuote(symbol, category, date, null);
                        if (quote != null && quote.success && quote.price > 0) {
                            double price = quote.price;
                            String currency = quote.currency != null ? quote.currency : (state.currency != null ? state.currency : "BRL");
                            if (currency != null && !"BRL".equalsIgnoreCase(currency)) {
                                double exchangeRate = quoteService.getExchangeRate(currency, "BRL");
                                price *= exchangeRate;
                            }
                            priceCache.put(cacheKey, price);
                            fetched++;
                        }
                        // Pequena pausa para não sobrecarregar a API
                        if (fetched % 10 == 0 && fetched > 0) {
                            try {
                                Thread.sleep(50); // 50ms de pausa a cada 10 requisições
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
            }
        }

        private static class AssetState {
            final String symbol;
            final String category;
            String currency;
            String tipoInvestimento;
            String tipoRentabilidade;
            String indice;
            Double percentualIndice;
            Double taxaFixa;
            LocalDate dataVencimento;
            final List<PositionLayer> layers = new ArrayList<>();
            double lastKnownPrice;

            private AssetState(String symbol, String category) {
                this.symbol = symbol;
                this.category = category;
            }

            static AssetState fromInvestment(Investimento inv) {
                AssetState state = new AssetState(
                    inv.getNome() != null ? inv.getNome() : "DESCONHECIDO",
                    inv.getCategoria() != null ? inv.getCategoria() : "OUTROS"
                );
                state.updateMetadata(inv);
                return state;
            }

            void updateMetadata(Investimento inv) {
                if (inv.getMoeda() != null) {
                    this.currency = inv.getMoeda();
                }
                this.tipoInvestimento = inv.getTipoInvestimento();
                this.tipoRentabilidade = inv.getTipoRentabilidade();
                this.indice = inv.getIndice();
                this.percentualIndice = inv.getPercentualIndice();
                this.taxaFixa = inv.getTaxaFixa();
                if (inv.getDataVencimento() != null) {
                    this.dataVencimento = inv.getDataVencimento();
                }
            }

            void applyTransaction(Investimento inv) {
                double qty = inv.getQuantidade();
                
                // Ignora transações com quantidade zero (não devem existir, mas protege contra bugs)
                if (qty == 0.0 || Double.isNaN(qty) || Double.isInfinite(qty)) {
                    return;
                }
                
                double unitCost = (qty != 0) ? Math.abs(inv.getValorAporte()) / Math.abs(qty) : 0.0;

                if (qty > 0) {
                    // Compra: adiciona nova camada
                    PositionLayer layer = new PositionLayer();
                    layer.qty = qty;
                    layer.unitCost = unitCost;
                    layer.aporteDate = inv.getDataAporte();
                    layer.originalAmount = Math.abs(inv.getValorAporte());
                    layers.add(layer);
                } else if (qty < 0) {
                    // Venda: remove quantidade das camadas existentes (FIFO)
                    double remaining = Math.abs(qty);
                    Iterator<PositionLayer> iterator = layers.iterator();
                    while (iterator.hasNext() && remaining > 0) {
                        PositionLayer layer = iterator.next();
                        double qtyToUse = Math.min(remaining, layer.qty);
                        layer.qty -= qtyToUse;
                        remaining -= qtyToUse;
                        // Remove camada se quantidade ficou muito pequena (arredondamento)
                        if (layer.qty <= 0.000001) {
                            iterator.remove();
                        }
                    }
                }
            }

            boolean isEmpty() {
                return layers.isEmpty();
            }

            double getTotalQuantity() {
                return layers.stream().mapToDouble(layer -> layer.qty).sum();
            }

            double getTotalCostBasis() {
                return layers.stream().mapToDouble(layer -> layer.qty * layer.unitCost).sum();
            }

            double getAverageCost() {
                double qty = getTotalQuantity();
                if (qty <= 0) {
                    return 0.0;
                }
                return getTotalCostBasis() / qty;
            }
        }

        private static class PositionLayer {
            double qty;
            double unitCost;
            LocalDate aporteDate;
            double originalAmount;
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
    
    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "Método não permitido");
                return;
            }
            Map<String, Object> response = new HashMap<>();
            response.put("status", "ok");
            response.put("timestamp", System.currentTimeMillis());
            sendJsonResponse(exchange, 200, response);
        }
    }
    
    private static int requireUserId(HttpExchange exchange) {
        Object attr = exchange.getAttribute("userId");
        if (attr instanceof Integer) {
            return (Integer) attr;
        }
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Token de autenticação ausente");
        }
        String token = authHeader.substring("Bearer ".length()).trim();
        JwtUtil.JwtValidationResult result = JwtUtil.validateToken(token);
        if (!result.isValid()) {
            throw new UnauthorizedException(result.getMessage());
        }
        exchange.setAttribute("userId", result.getUserId());
        return result.getUserId();
    }
    
    private static void handleUnauthorized(HttpExchange exchange, UnauthorizedException e) throws IOException {
        LOGGER.warning("Requisição não autorizada: " + e.getMessage());
        sendErrorResponse(exchange, 401, e.getMessage());
    }
    
    private static HttpHandler secure(HttpHandler delegate) {
        return exchange -> {
            try {
                int userId = requireUserId(exchange);
                exchange.setAttribute("userId", userId);
                delegate.handle(exchange);
            } catch (UnauthorizedException e) {
                handleUnauthorized(exchange, e);
            }
        };
    }
    
    private static final class PlainLogFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(record.getLevel().getLocalizedName()).append("] ")
              .append(formatMessage(record)).append(System.lineSeparator());
            if (record.getThrown() != null) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                record.getThrown().printStackTrace(pw);
                pw.flush();
                sb.append(sw);
            }
            return sb.toString();
        }
    }
    
    private static class UnauthorizedException extends RuntimeException {
        UnauthorizedException(String message) {
            super(message);
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
        if (json == null) return result;
        
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
        if (json == null) return result;
        
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
                    // IMPORTANTE: Normaliza formato brasileiro (vírgula) para formato internacional (ponto)
                    try {
                        String normalizedStr = normalizeBrazilianNumber(valueStr);
                        if (normalizedStr.contains(".")) {
                            value = Double.parseDouble(normalizedStr);
                        } else {
                            value = Integer.parseInt(normalizedStr);
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
        if (json == null) return result;
        
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
                    // IMPORTANTE: Normaliza formato brasileiro (vírgula) para formato internacional (ponto)
                    try {
                        String normalizedStr = normalizeBrazilianNumber(valueStr);
                        if (normalizedStr.contains(".")) {
                            value = Double.parseDouble(normalizedStr);
                        } else {
                            value = Integer.parseInt(normalizedStr);
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
        if ("userId".equals(paramName)) {
            return String.valueOf(requireUserId(exchange));
        }
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
    
    /**
     * Calcula informações da fatura de cartão de crédito baseado nos dias de fechamento e pagamento
     * @param diaFechamento Dia do mês em que a fatura fecha (1-31)
     * @param diaPagamento Dia do mês em que a fatura deve ser paga (1-31)
     * @return Map com informações da fatura (próximo fechamento, próximo pagamento, status, etc.)
     */
    private static Map<String, Object> calcularInfoFatura(int diaFechamento, int diaPagamento) {
        Map<String, Object> info = new HashMap<>();
        LocalDate hoje = LocalDate.now();
        
        // Calcula a próxima data de fechamento
        LocalDate proximoFechamento;
        
        // Tenta criar a data de fechamento no mês atual
        try {
            LocalDate fechamentoEsteMes = LocalDate.of(hoje.getYear(), hoje.getMonthValue(), diaFechamento);
            if (hoje.isBefore(fechamentoEsteMes) || hoje.isEqual(fechamentoEsteMes)) {
                // Ainda não fechou este mês (ou fecha hoje)
                proximoFechamento = fechamentoEsteMes;
            } else {
                // Já fechou este mês, próximo fechamento é no próximo mês
                // Calcula o próximo mês a partir do mês atual, não do fechamento
                LocalDate proximoMes = LocalDate.of(hoje.getYear(), hoje.getMonthValue(), 1).plusMonths(1);
                try {
                    proximoFechamento = proximoMes.withDayOfMonth(diaFechamento);
                } catch (java.time.DateTimeException e) {
                    // Se o dia não existe no próximo mês (ex: 31 de fevereiro), usa o último dia do mês
                    proximoFechamento = proximoMes.withDayOfMonth(proximoMes.lengthOfMonth());
                }
            }
        } catch (java.time.DateTimeException e) {
            // Se o dia não existe no mês atual (ex: 31 de fevereiro), usa o último dia do mês
            int ultimoDia = LocalDate.of(hoje.getYear(), hoje.getMonthValue(), 1).lengthOfMonth();
            LocalDate fechamentoEsteMes = LocalDate.of(hoje.getYear(), hoje.getMonthValue(), ultimoDia);
            if (hoje.isBefore(fechamentoEsteMes) || hoje.isEqual(fechamentoEsteMes)) {
                proximoFechamento = fechamentoEsteMes;
            } else {
                // Próximo mês
                LocalDate proximoMes = LocalDate.of(hoje.getYear(), hoje.getMonthValue(), 1).plusMonths(1);
                try {
                    proximoFechamento = proximoMes.withDayOfMonth(diaFechamento);
                } catch (java.time.DateTimeException e2) {
                    proximoFechamento = proximoMes.withDayOfMonth(proximoMes.lengthOfMonth());
                }
            }
        }
        
        // Calcula a próxima data de pagamento
        // Regra: o pagamento sempre acontece APÓS o fechamento
        // Se diaPagamento < diaFechamento, o pagamento é no mês seguinte ao fechamento
        // Exemplo: fechamento dia 29, pagamento dia 1 -> fecha dia 29/nov, paga dia 1/dez
        LocalDate proximoPagamento;
        if (diaPagamento < diaFechamento) {
            // Pagamento é no mês seguinte ao fechamento
            LocalDate mesPagamento = proximoFechamento.plusMonths(1);
            try {
                proximoPagamento = mesPagamento.withDayOfMonth(diaPagamento);
            } catch (java.time.DateTimeException e) {
                // Se o dia não existe no mês (ex: 31 de fevereiro), usa o último dia do mês
                proximoPagamento = mesPagamento.withDayOfMonth(mesPagamento.lengthOfMonth());
            }
        } else {
            // Pagamento é no mesmo mês do fechamento, mas sempre depois do fechamento
            try {
                proximoPagamento = proximoFechamento.withDayOfMonth(diaPagamento);
                // Se o pagamento calculado é antes ou igual ao fechamento, ajusta para o mês seguinte
                if (proximoPagamento.isBefore(proximoFechamento) || proximoPagamento.equals(proximoFechamento)) {
                    LocalDate mesPagamento = proximoFechamento.plusMonths(1);
                    try {
                        proximoPagamento = mesPagamento.withDayOfMonth(diaPagamento);
                    } catch (java.time.DateTimeException e) {
                        proximoPagamento = mesPagamento.withDayOfMonth(mesPagamento.lengthOfMonth());
                    }
                }
            } catch (java.time.DateTimeException e) {
                // Se o dia não existe no mês, vai para o próximo mês
                LocalDate mesPagamento = proximoFechamento.plusMonths(1);
                try {
                    proximoPagamento = mesPagamento.withDayOfMonth(diaPagamento);
                } catch (java.time.DateTimeException e2) {
                    proximoPagamento = mesPagamento.withDayOfMonth(mesPagamento.lengthOfMonth());
                }
            }
        }
        
        // Calcula a data do último fechamento (fechamento anterior)
        LocalDate ultimoFechamento;
        LocalDate mesAnterior = proximoFechamento.minusMonths(1);
        try {
            ultimoFechamento = mesAnterior.withDayOfMonth(diaFechamento);
        } catch (java.time.DateTimeException e) {
            // Se o dia não existe no mês (ex: 31 de fevereiro), usa o último dia do mês
            ultimoFechamento = mesAnterior.withDayOfMonth(mesAnterior.lengthOfMonth());
        }
        
        // Determina se a fatura atual está aberta ou fechada
        // Fatura está aberta se hoje está entre o último fechamento (inclusive) e o próximo fechamento (exclusive)
        boolean faturaAberta = !hoje.isBefore(ultimoFechamento) && hoje.isBefore(proximoFechamento);
        
        // Calcula dias até próximo fechamento e pagamento
        long diasAteFechamento = ChronoUnit.DAYS.between(hoje, proximoFechamento);
        long diasAtePagamento = ChronoUnit.DAYS.between(hoje, proximoPagamento);
        
        info.put("proximoFechamento", proximoFechamento.toString());
        info.put("proximoPagamento", proximoPagamento.toString());
        info.put("ultimoFechamento", ultimoFechamento.toString());
        info.put("faturaAberta", faturaAberta);
        info.put("diasAteFechamento", diasAteFechamento);
        info.put("diasAtePagamento", diasAtePagamento);
        
        return info;
    }
    
    private static void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        String jsonResponse = toJson(response);
        
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        
        exchange.sendResponseHeaders(statusCode, jsonResponse.getBytes("UTF-8").length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(jsonResponse.getBytes("UTF-8"));
        }
    }
}