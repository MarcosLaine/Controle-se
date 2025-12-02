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
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import server.security.*;
import server.validation.*;
import server.model.*;
import server.repository.*;
import server.handlers.*;
import server.database.DatabaseConnection;
import server.services.QuoteService;
import server.utils.AuthUtil;
import server.utils.ResponseUtil;
import server.utils.RequestUtil;
import server.utils.JsonUtil;
import server.utils.NumberUtil;
import server.utils.CreditCardUtil;
import static server.utils.AuthUtil.UnauthorizedException;

/**
 * Servidor HTTP simplificado para conectar Frontend com Backend Java
 * Implementa uma API REST básica para o sistema Controle-se
 */
public class ControleSeServer {
    private static final Logger LOGGER = Logger.getLogger(ControleSeServer.class.getName());
    private static HttpServer server;
    private static RateLimiter rateLimiter;
    private static LoginAttemptTracker loginAttemptTracker;
    private static CaptchaValidator captchaValidator;
    private static CircuitBreaker authCircuitBreaker;
    private static CircuitBreaker apiCircuitBreaker;
    
    // Lê a porta da variável de ambiente PORT (usada pelo Render) ou usa 8080 como padrão
    private static int getPort() {
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.isEmpty()) {
            try {
                return Integer.parseInt(portEnv);
            } catch (NumberFormatException e) {
                LOGGER.warning("Porta inválida na variável PORT: " + portEnv + ". Usando 8080 como padrão.");
            }
        }
        return 8080;
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
            // Inicializa componentes de segurança
            initializeSecurityComponents();
            
            // Inicializa o pool de conexões do banco de dados
            DatabaseConnection.getInstance();
            
            // Cria o servidor HTTP
            server = createServer();
            
            // Configura as rotas da API
            setupRoutes();
            
            // Inicia o servidor
            server.start();
            
            // Inicia os schedulers
            server.services.SchedulerService schedulerService = new server.services.SchedulerService();
            schedulerService.iniciarSchedulerRecorrencias();
            schedulerService.iniciarSchedulerCotacoes();
            
            // Exibe informações do servidor
            exibirInformacoesServidor();
            
            // Adiciona shutdown hook para limpar recursos
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOGGER.info("Encerrando servidor...");
                if (server != null) {
                    server.stop(5); // Para o servidor com delay de 5 segundos
                }
                if (rateLimiter != null) {
                    rateLimiter.shutdown();
                }
                if (loginAttemptTracker != null) {
                    loginAttemptTracker.shutdown();
                }
                DatabaseConnection.shutdown();
                LOGGER.info("Servidor encerrado.");
            }, "ShutdownHook"));
            
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
                
                HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(getPort()), 0);
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
        return HttpServer.create(new InetSocketAddress(getPort()), 0);
    }
    
    /**
     * Inicializa componentes de segurança (Rate Limiting, Login Tracking, Circuit Breaker, CAPTCHA)
     */
    private static void initializeSecurityComponents() {
        rateLimiter = new RateLimiter();
        loginAttemptTracker = new LoginAttemptTracker();
        captchaValidator = new CaptchaValidator();
        authCircuitBreaker = new CircuitBreaker("auth", 10, 60 * 1000, 3); // 10 falhas, 1 min timeout
        apiCircuitBreaker = new CircuitBreaker("api", 20, 60 * 1000, 5); // 20 falhas, 1 min timeout
        
        if (captchaValidator.isEnabled()) {
            LOGGER.info("Componentes de segurança inicializados (Rate Limiting, Login Tracking, Circuit Breaker, CAPTCHA)");
        } else {
            LOGGER.warning("CAPTCHA desabilitado: configure RECAPTCHA_SECRET_KEY para habilitar");
            LOGGER.info("Componentes de segurança inicializados (Rate Limiting, Login Tracking, Circuit Breaker)");
        }
    }
    
    /**
     * Exibe informações do servidor
     */
    private static void exibirInformacoesServidor() {
        LOGGER.info("=== SERVIDOR CONTROLE-SE INICIADO ===");
        int port = getPort();
        LOGGER.info("Servidor rodando em: http" + (server instanceof HttpsServer ? "s" : "") + "://localhost:" + port);
        LOGGER.info("Frontend disponível em: http" + (server instanceof HttpsServer ? "s" : "") + "://localhost:" + port + "/");
        LOGGER.info("API disponível em: http" + (server instanceof HttpsServer ? "s" : "") + "://localhost:" + port + "/api/");
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
                    server.services.RecurrenceService recurrenceService = new server.services.RecurrenceService();
                    int criados = recurrenceService.processarRecorrencias();
                    LOGGER.info("Total de transações recorrentes criadas: " + criados);
                } catch (Exception e) {
                    System.err.println("Erro ao processar recorrências: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                }
            }
        }, delay, periodo);
        
        // Para fins de DEBUG/TESTE: executa imediatamente na inicialização também
        LOGGER.info("[INICIALIZAÇÃO] Processando recorrências pendentes...");
        try {
            server.services.RecurrenceService recurrenceService = new server.services.RecurrenceService();
            int criados = recurrenceService.processarRecorrencias();
            if (criados > 0) {
                LOGGER.info("[INICIALIZAÇÃO] " + criados + " transações recorrentes criadas");
            } else {
                LOGGER.info("[INICIALIZAÇÃO] Nenhuma recorrência pendente");
            }
        } catch (Exception e) {
            System.err.println("[INICIALIZAÇÃO] Erro ao processar recorrências: " + e.getMessage());
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
    
    /**
     * Extrai o IP do cliente da requisição
     */
    private static String getClientIp(HttpExchange exchange) {
        // Tenta obter do header X-Forwarded-For (útil para proxies/load balancers)
        String forwardedFor = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            // Pega o primeiro IP (o IP original do cliente)
            String[] ips = forwardedFor.split(",");
            if (ips.length > 0) {
                return ips[0].trim();
            }
        }
        
        // Tenta obter do header X-Real-IP
        String realIp = exchange.getRequestHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp.trim();
        }
        
        // Fallback para o IP remoto da conexão
        InetSocketAddress remoteAddress = exchange.getRemoteAddress();
        if (remoteAddress != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        
        return "unknown";
    }
    
    /**
     * Cria um handler protegido com rate limiting e circuit breaker
     */
    private static HttpHandler withRateLimit(HttpHandler handler, String endpoint, CircuitBreaker circuitBreaker) {
        return new RateLimitHandler(handler, rateLimiter, circuitBreaker, endpoint);
    }
    
    private static void setupRoutes() {
        // Initialize repositories
        server.repository.UserRepository userRepository = new server.repository.UserRepository();
        server.repository.AccountRepository accountRepository = new server.repository.AccountRepository();
        server.repository.CategoryRepository categoryRepository = new server.repository.CategoryRepository();
        server.repository.ExpenseRepository expenseRepository = new server.repository.ExpenseRepository();
        server.repository.IncomeRepository incomeRepository = new server.repository.IncomeRepository();
        server.repository.BudgetRepository budgetRepository = new server.repository.BudgetRepository();
        server.repository.TagRepository tagRepository = new server.repository.TagRepository();
        server.repository.InvestmentRepository investmentRepository = new server.repository.InvestmentRepository();

        // API Routes básicas (devem vir antes do StaticFileHandler)
        // Endpoints de autenticação com proteção especial
        server.createContext("/api/auth/login", 
            withRateLimit(new server.handlers.LoginHandler(userRepository, loginAttemptTracker, captchaValidator), "/api/auth/login", authCircuitBreaker));
        server.createContext("/api/auth/register", 
            withRateLimit(new server.handlers.RegisterHandler(userRepository), "/api/auth/register", authCircuitBreaker));
        server.createContext("/api/auth/change-password", 
            withRateLimit(secure(new server.handlers.ChangePasswordHandler(userRepository)), "/api/auth/change-password", authCircuitBreaker));
        server.createContext("/api/auth/user", 
            withRateLimit(secure(new server.handlers.DeleteUserHandler(userRepository)), "/api/auth/user", authCircuitBreaker));
        
        // Endpoints da API com proteção padrão
        server.createContext("/api/dashboard/overview", 
            withRateLimit(secure(new server.handlers.OverviewHandler()), "/api/dashboard/overview", apiCircuitBreaker));
        server.createContext("/api/categories", 
            withRateLimit(secure(new server.handlers.CategoriesHandler()), "/api/categories", apiCircuitBreaker));
        server.createContext("/api/accounts", 
            withRateLimit(secure(new server.handlers.AccountsHandler()), "/api/accounts", apiCircuitBreaker));
        server.createContext("/api/transactions/recent", 
            withRateLimit(secure(new server.handlers.RecentTransactionsHandler()), "/api/transactions/recent", apiCircuitBreaker));
        server.createContext("/api/transactions", 
            withRateLimit(secure(new server.handlers.TransactionsHandler()), "/api/transactions", apiCircuitBreaker));
        server.createContext("/api/expenses", 
            withRateLimit(secure(new server.handlers.ExpensesHandler()), "/api/expenses", apiCircuitBreaker));
        server.createContext("/api/incomes", 
            withRateLimit(secure(new server.handlers.IncomesHandler()), "/api/incomes", apiCircuitBreaker));
        server.createContext("/api/budgets", 
            withRateLimit(secure(new server.handlers.BudgetsHandler()), "/api/budgets", apiCircuitBreaker));
        server.createContext("/api/tags", 
            withRateLimit(secure(new server.handlers.TagsHandler()), "/api/tags", apiCircuitBreaker));
        server.createContext("/api/reports", 
            withRateLimit(secure(new server.handlers.ReportsHandler(expenseRepository, incomeRepository, categoryRepository, accountRepository)), "/api/reports", apiCircuitBreaker));
        server.createContext("/api/investments", 
            withRateLimit(secure(new server.handlers.InvestmentsHandler()), "/api/investments", apiCircuitBreaker));
        server.createContext("/api/investments/evolution", 
            withRateLimit(secure(new server.handlers.InvestmentEvolutionHandler(investmentRepository)), "/api/investments/evolution", apiCircuitBreaker));
        server.createContext("/api/investments/quote", 
            withRateLimit(secure(new server.handlers.InvestmentQuoteHandler()), "/api/investments/quote", apiCircuitBreaker));
        server.createContext("/api/tools/compound-interest", 
            withRateLimit(secure(new server.handlers.CompoundInterestHandler()), "/api/tools/compound-interest", apiCircuitBreaker));
        
        // Health check
        server.createContext("/health", new server.handlers.HealthHandler());
        
        // Servir arquivos estáticos (HTML, CSS, JS) - deve vir por último
        server.createContext("/", new server.handlers.StaticFileHandler());
        
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
    
    // ===== UTILITY METHODS =====
    
    // Nota: Todos os handlers foram extraídos para server.handlers.*
    // Os métodos utilitários abaixo são mantidos apenas para compatibilidade
    // e serão removidos quando todas as referências forem atualizadas
    
    private static int requireUserId(HttpExchange exchange) {
        return AuthUtil.requireUserId(exchange);
    }
    
    private static void handleUnauthorized(HttpExchange exchange, UnauthorizedException e) throws IOException {
        LOGGER.warning("Requisição não autorizada: " + e.getMessage());
        ResponseUtil.sendErrorResponse(exchange, 401, e.getMessage());
    }
    
    private static HttpHandler secure(HttpHandler delegate) {
        return exchange -> {
            try {
                int userId = AuthUtil.requireUserId(exchange);
                exchange.setAttribute("userId", userId);
                delegate.handle(exchange);
            } catch (UnauthorizedException e) {
                handleUnauthorized(exchange, e);
            }
        };
    }
    
    // Métodos utilitários mantidos para compatibilidade (serão removidos quando todas as referências forem atualizadas)
    
    private static String readRequestBody(HttpExchange exchange) throws IOException {
        return RequestUtil.readRequestBody(exchange);
    }
    
    private static Map<String, String> parseJson(String json) {
        return JsonUtil.parseJson(json);
    }
    
    private static Map<String, Object> parseJsonWithNested(String json) {
        return JsonUtil.parseJsonWithNested(json);
    }
    
    private static String toJson(Object obj) {
        return JsonUtil.toJson(obj);
    }
    
    private static List<Object> parseJsonArray(String json) {
        return JsonUtil.parseJsonArray(json);
    }
    
    private static String getQueryParam(HttpExchange exchange, String paramName) {
        return RequestUtil.getQueryParam(exchange, paramName);
    }
    
    private static void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        ResponseUtil.sendJsonResponse(exchange, statusCode, data);
    }
    
    private static void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        ResponseUtil.sendErrorResponse(exchange, statusCode, message);
    }
    
    private static Map<String, Object> calcularInfoFatura(int diaFechamento, int diaPagamento) {
        return CreditCardUtil.calcularInfoFatura(diaFechamento, diaPagamento);
    }
    
    // Fim dos métodos utilitários de compatibilidade
    
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
}