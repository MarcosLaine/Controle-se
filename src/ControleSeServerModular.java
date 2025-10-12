import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;

/**
 * Servidor HTTP Modularizado para o sistema Controle-se
 * Versão refatorada com arquitetura limpa e separação de responsabilidades
 */
public class ControleSeServerModular {
    private static final int PORT = 8080;
    private static HttpServer server;
    private static BancoDados bancoDados;
    
    public static void main(String[] args) {
        try {
            // Inicializa o banco de dados
            bancoDados = new BancoDados();
            
            // Cria o servidor HTTP
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            
            // Configura as rotas
            setupRoutes();
            
            // Inicia o servidor
            server.start();
            
            System.out.println("=== SERVIDOR CONTROLE-SE MODULAR INICIADO ===");
            System.out.println("Servidor rodando em: http://localhost:" + PORT);
            System.out.println("Frontend disponível em: http://localhost:" + PORT + "/");
            System.out.println("API disponível em: http://localhost:" + PORT + "/api/");
            System.out.println("Pressione Ctrl+C para parar o servidor");
            System.out.println("\n✓ Arquitetura Modular Ativa:");
            System.out.println("  - Handlers separados por responsabilidade");
            System.out.println("  - Utilitários reutilizáveis");
            System.out.println("  - Código organizado e manutenível");
            
        } catch (IOException e) {
            System.err.println("Erro ao iniciar servidor: " + e.getMessage());
        }
    }
    
    /**
     * Configura todas as rotas do servidor
     */
    private static void setupRoutes() {
        // Arquivos estáticos (HTML, CSS, JS)
        server.createContext("/", new StaticFileHandler());
        
        // API - Handlers modulares
        server.createContext("/api/categories", new CategoriesHandler(bancoDados));
        server.createContext("/api/accounts", new AccountsHandler(bancoDados));
        
        // Os handlers abaixo ainda usam a implementação antiga (você pode modularizar depois)
        server.createContext("/api/auth/login", new ControleSeServer.LoginHandler());
        server.createContext("/api/auth/register", new ControleSeServer.RegisterHandler());
        server.createContext("/api/dashboard/overview", new ControleSeServer.OverviewHandler());
        server.createContext("/api/transactions", new ControleSeServer.TransactionsHandler());
        server.createContext("/api/expenses", new ControleSeServer.ExpensesHandler());
        server.createContext("/api/incomes", new ControleSeServer.IncomesHandler());
        server.createContext("/api/budgets", new ControleSeServer.BudgetsHandler());
        
        // Configura executor
        server.setExecutor(null);
    }
}

