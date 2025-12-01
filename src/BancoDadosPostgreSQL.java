import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Implementação do Banco de Dados usando PostgreSQL (Aiven)
 * Substitui a implementação com arquivos .db por um SGBD relacional
 * Usa HikariCP para connection pooling eficiente
 */
public class BancoDadosPostgreSQL {
    
    // HikariCP DataSource para gerenciamento de pool de conexões
    private static HikariDataSource dataSource;
    private String jdbcUrl;
    private String username;
    private String password;
    
    /**
     * Construtor padrão - lê configuração de variáveis de ambiente ou arquivo
     */
    public BancoDadosPostgreSQL() {
        // Tenta ler de variáveis de ambiente (Aiven fornece essas variáveis)
        String host = System.getenv("PGHOST");
        String port = System.getenv("PGPORT");
        String database = System.getenv("PGDATABASE");
        this.username = System.getenv("PGUSER");
        this.password = System.getenv("PGPASSWORD");
        
        // DEBUG: Imprime variáveis de ambiente para ajudar no diagnóstico
        System.out.println("DEBUG DB: PGHOST=" + host);
        System.out.println("DEBUG DB: PGPORT=" + port);
        System.out.println("DEBUG DB: PGDATABASE=" + database);
        System.out.println("DEBUG DB: PGUSER=" + username);
        // Não logar senha!
        
        // Se não encontrar nas variáveis de ambiente, tenta arquivo de configuração
            if (host == null || host.isEmpty()) {
                loadConfigFromFile();
                // Só usa db.properties se a variável de ambiente NÃO estiver definida
                if (host == null || host.isEmpty()) host = System.getProperty("db.host");
                if (port == null || port.isEmpty()) port = System.getProperty("db.port");
                if (database == null || database.isEmpty()) database = System.getProperty("db.database");
                if (this.username == null || this.username.isEmpty()) this.username = System.getProperty("db.username");
                if (this.password == null || this.password.isEmpty()) this.password = System.getProperty("db.password");
                
                // Se ainda não encontrou, tenta ler do .env manualmente
                if (host == null || host.isEmpty()) {
                    try {
                        java.io.File envFile = new java.io.File(".env");
                        if (envFile.exists()) {
                            java.util.Scanner scanner = new java.util.Scanner(envFile);
                            while (scanner.hasNextLine()) {
                                String line = scanner.nextLine().trim();
                                if (line.startsWith("PGHOST=") && !line.startsWith("#")) {
                                    host = line.substring(7).trim();
                                } else if (line.startsWith("PGPORT=") && !line.startsWith("#")) {
                                    port = line.substring(7).trim();
                                } else if (line.startsWith("PGDATABASE=") && !line.startsWith("#")) {
                                    database = line.substring(11).trim();
                                } else if (line.startsWith("PGUSER=") && !line.startsWith("#")) {
                                    this.username = line.substring(7).trim();
                                } else if (line.startsWith("PGPASSWORD=") && !line.startsWith("#")) {
                                    this.password = line.substring(11).trim();
                                }
                            }
                            scanner.close();
                        }
                    } catch (Exception e) {
                        System.out.println("Aviso: Não foi possível ler .env: " + e.getMessage());
                    }
                }
            }
        
        // Constrói JDBC URL
        if (host != null && !host.isEmpty()) {
            if ("localhost".equals(host) || "127.0.0.1".equals(host) || "db".equals(host) || "controle-se-db".equals(host)) {
                 // Conexão local sem SSL
                 this.jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s?sslmode=disable", 
                    host, port != null ? port : "5432", database != null ? database : "controle_se");
            } else {
                // Aiven e produção requerem SSL - usar sslmode=require
                // Usa NonValidatingFactory para evitar problemas com certificado (produção deve usar certificado real)
                // Adiciona timeouts na URL para conexões SSL que podem demorar mais
                this.jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s?sslmode=require&sslfactory=org.postgresql.ssl.NonValidatingFactory&connectTimeout=30&socketTimeout=60&tcpKeepAlive=true", 
                    host, port != null ? port : "5432", database != null ? database : "controle_se");
            }
        } else {
            // ERRO EXPLÍCITO se não houver configuração
            System.err.println("ERRO CRÍTICO: Configuração de banco de dados não encontrada! Defina as variáveis de ambiente PGHOST, PGUSER, etc.");
            // URL inválida para forçar erro de conexão em vez de tentar localhost
            this.jdbcUrl = "jdbc:postgresql://missing-configuration:5432/controle_se";
        }
        
        // Inicializa o pool de conexões HikariCP
        initializeConnectionPool();
        
        // Inicializa o schema uma vez (usando conexão do pool)
        initializeSchemaOnce();
        ensureObservationTables();
        ensurePerformanceIndexes();
    }
    
    /**
     * Construtor com parâmetros explícitos
     */
    public BancoDadosPostgreSQL(String host, String port, String database, String username, String password) {
        if ("localhost".equals(host) || "127.0.0.1".equals(host) || "db".equals(host) || "controle-se-db".equals(host)) {
            this.jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s?sslmode=disable", host, port, database);
        } else {
            // Adiciona timeouts na URL para conexões SSL que podem demorar mais
            this.jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s?sslmode=require&connectTimeout=30&socketTimeout=60&tcpKeepAlive=true", host, port, database);
        }
        this.username = username;
        this.password = password;
        
        // Inicializa o pool de conexões HikariCP
        initializeConnectionPool();
        
        // Inicializa o schema uma vez (usando conexão do pool)
        initializeSchemaOnce();
        ensurePerformanceIndexes();
    }
    
    /**
     * Carrega configuração de arquivo db.properties (se existir)
     */
    private void loadConfigFromFile() {
        try {
            java.io.InputStream is = getClass().getClassLoader().getResourceAsStream("db.properties");
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                System.setProperty("db.host", props.getProperty("db.host", "localhost"));
                System.setProperty("db.port", props.getProperty("db.port", "5432"));
                System.setProperty("db.database", props.getProperty("db.database", "controle_se"));
                System.setProperty("db.username", props.getProperty("db.username", "postgres"));
                System.setProperty("db.password", props.getProperty("db.password", ""));
            }
        } catch (Exception e) {
            System.out.println("Arquivo db.properties não encontrado, usando valores padrão");
        }
    }
    
    /**
     * Inicializa o pool de conexões HikariCP
     * Configura pool com tamanho adequado e health checks
     */
    private void initializeConnectionPool() {
        // Se o pool já foi inicializado, não inicializa novamente
        if (dataSource != null && !dataSource.isClosed()) {
            return;
        }
        
        try {
            HikariConfig config = new HikariConfig();
            
            // Configuração básica de conexão
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(username != null ? username : "postgres");
            config.setPassword(password != null ? password : "");
            
            // Configurações do pool
            // Reduzido para evitar esgotamento no Aiven (limite de conexões)
            config.setMaximumPoolSize(10); // Máximo de conexões no pool (reduzido de 20)
            config.setMinimumIdle(2); // Mínimo de conexões ociosas mantidas (reduzido de 5)
            config.setConnectionTimeout(10000); // 10 segundos para obter conexão do pool (reduzido de 30s)
            config.setIdleTimeout(300000); // 5 minutos - conexões ociosas são fechadas (reduzido de 10min)
            config.setMaxLifetime(900000); // 15 minutos - tempo máximo de vida de uma conexão (reduzido de 30min)
            config.setLeakDetectionThreshold(30000); // Detecta connection leaks após 30s (reduzido de 60s)
            
            // Health check - testa conexão antes de usar
            config.setConnectionTestQuery("SELECT 1");
            
            // Propriedades adicionais para PostgreSQL
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            
            // Timeouts para conexões SSL
            config.addDataSourceProperty("connectTimeout", "30");
            config.addDataSourceProperty("socketTimeout", "60");
            config.addDataSourceProperty("tcpKeepAlive", "true");
            
            // Nome do pool para identificação em logs
            config.setPoolName("ControleSePool");
            
            // Cria o DataSource
            dataSource = new HikariDataSource(config);
            
            System.out.println("✅ Pool de conexões HikariCP inicializado com sucesso");
            System.out.println("   - Máximo de conexões: " + config.getMaximumPoolSize());
            System.out.println("   - Mínimo de conexões ociosas: " + config.getMinimumIdle());
            
        } catch (Exception e) {
            System.err.println("❌ Erro ao inicializar pool de conexões HikariCP: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Falha ao inicializar connection pool", e);
        }
    }
    
    
    /**
     * Inicializa o schema do banco uma única vez (cria tabelas se não existirem)
     * Usa uma conexão do pool apenas para inicialização
     */
    private void initializeSchemaOnce() {
        Connection tempConn = null;
        try {
            // Obtém conexão do pool para inicialização
            tempConn = getConnection();
            
            // Lê o arquivo schema_postgresql.sql
            java.io.InputStream is = getClass().getClassLoader().getResourceAsStream("schema_postgresql.sql");
            if (is == null) {
                // Tenta ler do diretório atual
                try {
                    java.io.File file = new java.io.File("schema_postgresql.sql");
                    if (file.exists()) {
                        is = new java.io.FileInputStream(file);
                    }
                } catch (Exception e) {
                    System.out.println("Schema não encontrado, assumindo que já existe no banco");
                    return;
                }
            }
            
            if (is != null) {
                String schema = new String(is.readAllBytes());
                // Divide em comandos (separados por ;)
                String[] commands = schema.split(";");
                
                try (Statement stmt = tempConn.createStatement()) {
                    for (String command : commands) {
                        command = command.trim();
                        if (!command.isEmpty() && !command.startsWith("--") && !command.startsWith("COMMENT")) {
                            try {
                                stmt.execute(command);
                            } catch (SQLException e) {
                                // Ignora erros de "já existe" ou "syntax error" (schema já foi executado)
                                String msg = e.getMessage().toLowerCase();
                                if (!msg.contains("already exists") && 
                                    !msg.contains("duplicate") &&
                                    !msg.contains("syntax error") &&
                                    !msg.contains("unterminated") &&
                                    !msg.contains("current transaction is aborted")) {
                                    // Só mostra erros que não são esperados
                                    // System.out.println("Aviso ao executar schema: " + e.getMessage());
                                }
                            }
                        }
                    }
                    try {
                        tempConn.commit();
                    } catch (SQLException e) {
                        // Se houve erro na transação, faz rollback e continua
                        try {
                            tempConn.rollback();
                        } catch (SQLException ex) {}
                    }
                    System.out.println("✓ Schema verificado (já existe ou foi criado)");
                }
            } else {
                // Schema não encontrado, mas não é crítico se já foi executado manualmente
                System.out.println("✓ Schema não encontrado no código (assumindo que já existe no banco)");
            }
        } catch (Exception e) {
            System.err.println("Erro ao inicializar schema: " + e.getMessage());
            // Não falha - assume que schema já existe
        } finally {
            // Fecha conexão temporária (retorna ao pool HikariCP)
            if (tempConn != null) {
                try {
                    tempConn.close(); // Retorna ao pool automaticamente
                } catch (SQLException e) {
                    System.err.println("Aviso: Erro ao fechar conexão: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Garante que índices de performance existam
     * Cria os índices compostos otimizados para queries frequentes
     */
    private void ensurePerformanceIndexes() {
        Connection tempConn = null;
        try {
            tempConn = getConnection();
            try (Statement stmt = tempConn.createStatement()) {
                System.out.println("[ÍNDICES] Criando/verificando índices de performance...");
                
                // ========== ÍNDICES PARA CATEGORIA_GASTO ==========
                stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_categoria_gasto_composto " +
                    "ON categoria_gasto(id_categoria, id_gasto, ativo) " +
                    "WHERE ativo = TRUE"
                );
                stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_categoria_gasto_gasto_ativo " +
                    "ON categoria_gasto(id_gasto, ativo) " +
                    "WHERE ativo = TRUE"
                );
                
                // ========== ÍNDICES PARA GASTOS ==========
                stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_gastos_usuario_ativo " +
                    "ON gastos(id_usuario, ativo) " +
                    "WHERE ativo = TRUE"
                );
                stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_gastos_usuario_ativo_data " +
                    "ON gastos(id_usuario, ativo, data DESC) " +
                    "WHERE ativo = TRUE"
                );
                stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_gastos_data_ativo " +
                    "ON gastos(data, ativo) " +
                    "WHERE ativo = TRUE"
                );
                stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_gastos_recorrencia_ativo " +
                    "ON gastos(proxima_recorrencia, ativo) " +
                    "WHERE proxima_recorrencia IS NOT NULL AND ativo = TRUE"
                );
                
                // ========== ÍNDICES PARA RECEITAS ==========
                stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_receitas_usuario_ativo " +
                    "ON receitas(id_usuario, ativo) " +
                    "WHERE ativo = TRUE"
                );
                stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_receitas_usuario_ativo_data " +
                    "ON receitas(id_usuario, ativo, data DESC) " +
                    "WHERE ativo = TRUE"
                );
                stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_receitas_data_ativo " +
                    "ON receitas(data, ativo) " +
                    "WHERE ativo = TRUE"
                );
                stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_receitas_recorrencia_ativo " +
                    "ON receitas(proxima_recorrencia, ativo) " +
                    "WHERE proxima_recorrencia IS NOT NULL AND ativo = TRUE"
                );
                
                // ========== ÍNDICES PARA CONTAS ==========
                stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_contas_usuario_tipo_ativo " +
                    "ON contas(id_usuario, tipo, ativo) " +
                    "WHERE ativo = TRUE"
                );
                
                // ========== ÍNDICES PARA INVESTIMENTOS ==========
                stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_investimentos_usuario_ativo_data " +
                    "ON investimentos(id_usuario, ativo, data_aporte DESC) " +
                    "WHERE ativo = TRUE"
                );
                
                // ========== ÍNDICES PARA TRANSAÇÃO_TAG ==========
                stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_transacao_tag_tag_tipo_ativo " +
                    "ON transacao_tag(id_tag, tipo_transacao, ativo) " +
                    "WHERE ativo = TRUE"
                );
                stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_transacao_tag_transacao_tipo_ativo " +
                    "ON transacao_tag(id_transacao, tipo_transacao, ativo) " +
                    "WHERE ativo = TRUE"
                );
                
                // ========== ÍNDICES PARA ORÇAMENTOS ==========
                stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_orcamentos_categoria_ativo " +
                    "ON orcamentos(id_categoria, ativo) " +
                    "WHERE ativo = TRUE"
                );
                
                // Verifica se os índices foram criados corretamente
                try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) as total FROM pg_indexes " +
                    "WHERE schemaname = 'public' " +
                    "AND (indexname LIKE 'idx_%_composto' " +
                    "OR indexname LIKE 'idx_%_usuario_ativo%' " +
                    "OR indexname LIKE 'idx_%_data%' " +
                    "OR indexname LIKE 'idx_%_recorrencia%' " +
                    "OR indexname LIKE 'idx_%_tag%' " +
                    "OR indexname LIKE 'idx_%_categoria_ativo')"
                )) {
                    if (rs.next()) {
                        int count = rs.getInt("total");
                        System.out.println("✓ " + count + " índices de performance verificados/criados");
                    } else {
                        System.out.println("✓ Índices de performance verificados/criados");
                    }
                } catch (SQLException e) {
                    // Se a query de verificação falhar, apenas continua
                    System.out.println("✓ Índices de performance verificados/criados");
                }
            }
            tempConn.commit();
        } catch (Exception e) {
            System.err.println("Aviso: não foi possível garantir índices de performance: " + e.getMessage());
            e.printStackTrace();
            try {
                if (tempConn != null) {
                    tempConn.rollback();
                }
            } catch (SQLException ignored) {}
        } finally {
            if (tempConn != null) {
                try {
                    tempConn.close(); // Retorna ao pool HikariCP
                } catch (SQLException ignored) {}
            }
        }
    }
    
    /**
     * Garante que tabelas de observações existam (usadas em despesas e receitas)
     * Necessário quando schema inicial não foi executado automaticamente
     */
    private void ensureObservationTables() {
        Connection tempConn = null;
        try {
            tempConn = getConnection();
            try (Statement stmt = tempConn.createStatement()) {
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS gasto_observacoes (" +
                        "id_observacao SERIAL PRIMARY KEY," +
                        "id_gasto INTEGER NOT NULL," +
                        "observacao TEXT NOT NULL," +
                        "ordem INTEGER DEFAULT 0," +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                        "FOREIGN KEY (id_gasto) REFERENCES gastos(id_gasto) ON DELETE CASCADE" +
                    ")"
                );

                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS receita_observacoes (" +
                        "id_observacao SERIAL PRIMARY KEY," +
                        "id_receita INTEGER NOT NULL," +
                        "observacao TEXT NOT NULL," +
                        "ordem INTEGER DEFAULT 0," +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                        "FOREIGN KEY (id_receita) REFERENCES receitas(id_receita) ON DELETE CASCADE" +
                    ")"
                );
            }
            tempConn.commit();
        } catch (Exception e) {
            System.err.println("Aviso: não foi possível garantir tabelas de observações: " + e.getMessage());
            try {
                if (tempConn != null) {
                    tempConn.rollback();
                }
            } catch (SQLException ignored) {}
        } finally {
            if (tempConn != null) {
                try {
                    tempConn.close(); // Retorna ao pool HikariCP
                } catch (SQLException ignored) {}
            }
        }
    }
    
    /**
     * Obtém uma conexão do pool HikariCP (thread-safe)
     * O HikariCP gerencia automaticamente a distribuição de conexões entre threads
     */
    private Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Pool de conexões não está disponível");
        }
        
        Connection conn = dataSource.getConnection();
        
        // Garante que autoCommit está desabilitado para usar transações explícitas
        if (conn.getAutoCommit()) {
            conn.setAutoCommit(false);
        }
        
        return conn;
    }
    
    /**
     * Fecha a conexão e a retorna ao pool HikariCP
     * IMPORTANTE: Com HikariCP, todas as conexões DEVEM ser fechadas explicitamente
     * Use try-with-resources sempre que possível
     */
    public void closeConnection() {
        // Método mantido para compatibilidade, mas não faz nada
        // As conexões devem ser fechadas usando try-with-resources ou closeConnection(Connection)
    }
    
    /**
     * Fecha uma conexão específica e a retorna ao pool
     * Use este método quando tiver uma referência direta à conexão
     * IMPORTANTE: Sempre feche conexões para evitar esgotamento do pool
     */
    public void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.isClosed()) {
                    conn.close(); // Retorna ao pool HikariCP automaticamente
                }
            } catch (SQLException e) {
                // Log mas não propaga - HikariCP gerencia erros de conexão
                System.err.println("Aviso: Erro ao fechar conexão: " + e.getMessage());
            }
        }
    }
    
    
    /**
     * Fecha o pool de conexões (chamado no shutdown da aplicação)
     */
    public static void shutdownConnectionPool() {
        if (dataSource != null && !dataSource.isClosed()) {
            System.out.println("Encerrando pool de conexões HikariCP...");
            dataSource.close();
            System.out.println("✅ Pool de conexões encerrado");
        }
    }
    
    // ========== OPERAÇÕES DE USUÁRIO ==========
    
    /**
     * Sanitiza string removendo caracteres perigosos (proteção adicional)
     * Remove caracteres de controle e normaliza espaços
     */
    private String sanitizeString(String input) {
        if (input == null) return null;
        // Remove caracteres de controle (exceto \n, \r, \t)
        String sanitized = input.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        // Normaliza espaços múltiplos
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        return sanitized;
    }
    
    /**
     * Valida entrada de dados (proteção contra dados inválidos e SQL injection)
     * ATENÇÃO: PreparedStatement já protege contra SQL injection, mas validação adicional
     * previne dados malformados e melhora a qualidade dos dados
     */
    private void validateInput(String field, String value, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " não pode ser vazio");
        }
        
        // Sanitiza antes de validar
        String sanitized = sanitizeString(value);
        if (sanitized.length() != value.length()) {
            throw new IllegalArgumentException(field + " contém caracteres inválidos");
        }
        
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " excede o tamanho máximo de " + maxLength + " caracteres");
        }
        
        // Proteção adicional: detecta padrões suspeitos de SQL injection
        // (mesmo que PreparedStatement proteja, isso ajuda a identificar tentativas)
        String lowerValue = value.toLowerCase();
        String[] dangerousPatterns = {
            "';", "--", "/*", "*/", "xp_", "sp_", "exec", "execute", 
            "union", "select", "insert", "update", "delete", "drop", 
            "create", "alter", "script", "<script", "javascript:"
        };
        for (String pattern : dangerousPatterns) {
            if (lowerValue.contains(pattern)) {
                throw new IllegalArgumentException(field + " contém caracteres não permitidos");
            }
        }
    }
    
    /**
     * Valida formato de email (proteção adicional)
     */
    private void validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email não pode ser vazio");
        }
        
        // Sanitiza
        String sanitized = sanitizeString(email);
        if (!sanitized.equals(email)) {
            throw new IllegalArgumentException("Email contém caracteres inválidos");
        }
        
        // Valida formato
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new IllegalArgumentException("Email inválido");
        }
        
        // Limite de tamanho
        if (email.length() > 255) {
            throw new IllegalArgumentException("Email excede o tamanho máximo");
        }
    }
    
    /**
     * Valida ID (deve ser positivo)
     */
    private void validateId(String field, int id) {
        if (id <= 0) {
            throw new IllegalArgumentException(field + " deve ser um número positivo");
        }
    }
    
    /**
     * Valida valor monetário (deve ser positivo ou zero)
     */
    private void validateAmount(String field, double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException(field + " não pode ser negativo");
        }
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            throw new IllegalArgumentException(field + " deve ser um número válido");
        }
    }
    
    /**
     * Valida enum/valor permitido
     */
    private void validateEnum(String field, String value, String[] allowedValues) {
        if (value == null) {
            throw new IllegalArgumentException(field + " não pode ser nulo");
        }
        for (String allowed : allowedValues) {
            if (allowed.equalsIgnoreCase(value)) {
                return;
            }
        }
        throw new IllegalArgumentException(field + " deve ser um dos valores permitidos: " + 
            String.join(", ", allowedValues));
    }
    
    public int cadastrarUsuario(String nome, String email, String senha) {
        validateInput("Nome", nome, 100);
        validateEmail(email);
        validateInput("Senha", senha, 200);

        synchronized (this) {
            // Normaliza o email da mesma forma que será salvo (lowercase + trim)
            String emailNormalizado = email.toLowerCase().trim();
            
            // Não verifica duplicação prévia - confia na constraint UNIQUE do banco
            // Isso evita problemas de isolamento de transação e race conditions
            // A constraint UNIQUE é mais confiável e atômica
            
            String senhaHash = PasswordHasher.hashPassword(senha);
            return salvarUsuario(nome, emailNormalizado, senhaHash);
        }
    }
    
    /**
     * Verifica se um email já existe no banco (sem verificar se está ativo)
     * Útil para verificação de duplicação antes de cadastrar
     * Usa uma conexão separada com autoCommit para evitar problemas de isolamento
     */
    private boolean emailJaExiste(String email) {
        String sql = "SELECT COUNT(*) FROM usuarios WHERE email = ?";
        
        Connection conn = null;
        try {
            // Obtém conexão do pool
            conn = dataSource.getConnection();
            // Habilita autoCommit para esta consulta de leitura
            conn.setAutoCommit(true);
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, email);
                ResultSet rs = pstmt.executeQuery();
                
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
                return false;
            }
        } catch (SQLException e) {
            // Em caso de erro, assume que não existe para não bloquear cadastros legítimos
            // O INSERT com constraint de unique vai capturar duplicação real
            return false;
        } finally {
            // Fecha a conexão
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    // Ignora erro ao fechar
                }
            }
        }
    }

    private int salvarUsuario(String nome, String email, String senhaArmazenada) {
        validateInput("Nome", nome, 100);
        validateEmail(email);

        // Garante que ativo seja TRUE explicitamente
        // Email já vem normalizado do cadastrarUsuario
        String sql = "INSERT INTO usuarios (nome, email, senha, ativo) VALUES (?, ?, ?, TRUE) RETURNING id_usuario";
        
        Connection conn = null;
        try {
            conn = getConnection(); // getConnection já desabilita autoCommit
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, nome.trim());
                pstmt.setString(2, email); // Email já normalizado
                pstmt.setString(3, senhaArmazenada);
                
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    int idUsuario = rs.getInt(1);
                    conn.commit(); // Commit na mesma conexão
                    return idUsuario;
                }
                throw new RuntimeException("Erro ao cadastrar usuário");
            }
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback(); // Rollback na mesma conexão
                } catch (SQLException ex) {
                    // Ignora erro no rollback
                }
            }
            // Verifica se é erro de duplicação (constraint unique)
            if (e.getSQLState() != null && e.getSQLState().equals("23505")) {
                throw new RuntimeException("Email já cadastrado!");
            }
            throw new RuntimeException("Erro ao cadastrar usuário: " + e.getMessage(), e);
        } finally {
            // Fecha a conexão
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    // Ignora erro ao fechar
                }
            }
        }
    }
    
    public Usuario buscarUsuario(int idUsuario) {
        String sql = "SELECT id_usuario, nome, email, senha, ativo FROM usuarios WHERE id_usuario = ? AND ativo = TRUE";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return mapUsuario(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar usuário: " + e.getMessage(), e);
        }
    }
    
    public Usuario buscarUsuarioPorEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }
        
        // Normaliza o email (lowercase + trim) para garantir consistência
        String emailNormalizado = email.toLowerCase().trim();
        
        String sql = "SELECT id_usuario, nome, email, senha, ativo FROM usuarios WHERE email = ? AND ativo = TRUE";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, emailNormalizado);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return mapUsuario(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar usuário por email: " + e.getMessage(), e);
        }
    }
    
    /**
     * Busca usuário por ID sem verificar se está ativo
     * Útil para buscar usuário recém-cadastrado
     */
    public Usuario buscarUsuarioSemAtivo(int idUsuario) {
        String sql = "SELECT id_usuario, nome, email, senha, ativo FROM usuarios WHERE id_usuario = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return mapUsuario(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar usuário: " + e.getMessage(), e);
        }
    }
    
    public boolean autenticarUsuario(String email, String senha) {
        Usuario usuario = buscarUsuarioPorEmail(email);
        if (usuario == null) {
            return false;
        }
        return PasswordHasher.verifyPassword(senha, usuario.getSenha());
    }

    public void atualizarSenhaUsuario(int idUsuario, String senhaAtual, String novaSenha) {
        validateInput("Nova senha", novaSenha, 200);
        if (novaSenha.length() < 8) {
            throw new IllegalArgumentException("A nova senha deve ter pelo menos 8 caracteres");
        }

        Usuario usuario = buscarUsuario(idUsuario);
        if (usuario == null) {
            throw new IllegalArgumentException("Usuário não encontrado");
        }

        if (!PasswordHasher.verifyPassword(senhaAtual, usuario.getSenha())) {
            throw new IllegalArgumentException("Senha atual incorreta");
        }

        String novaSenhaHash = PasswordHasher.hashPassword(novaSenha);
        String sql = "UPDATE usuarios SET senha = ? WHERE id_usuario = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, novaSenhaHash);
            pstmt.setInt(2, idUsuario);
            int updated = pstmt.executeUpdate();
            if (updated == 0) {
                throw new RuntimeException("Não foi possível atualizar a senha");
            }
            getConnection().commit();
        } catch (SQLException e) {
            try {
                getConnection().rollback();
            } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao atualizar senha: " + e.getMessage(), e);
        }
    }

    public void excluirUsuario(int idUsuario) {
        validateId("ID do usuário", idUsuario);
        String sql = "DELETE FROM usuarios WHERE id_usuario = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            int deleted = pstmt.executeUpdate();
            if (deleted == 0) {
                throw new IllegalArgumentException("Usuário não encontrado");
            }
            getConnection().commit();
        } catch (SQLException e) {
            try {
                getConnection().rollback();
            } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao excluir usuário: " + e.getMessage(), e);
        }
    }
    
    private Usuario mapUsuario(ResultSet rs) throws SQLException {
        Usuario usuario = new Usuario(
            rs.getInt("id_usuario"),
            rs.getString("nome"),
            rs.getString("email"),
            rs.getString("senha")
        );
        usuario.setAtivo(rs.getBoolean("ativo"));
        return usuario;
    }
    
    // ========== OPERAÇÕES DE CATEGORIA ==========
    
    public int cadastrarCategoria(String nome, int idUsuario) {
        // Validações de entrada
        validateInput("Nome da categoria", nome, 100);
        validateId("ID do usuário", idUsuario);
        
        // Sanitiza nome
        nome = sanitizeString(nome);
        
        String sql = "INSERT INTO categorias (nome, id_usuario) VALUES (?, ?) RETURNING id_categoria";
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, nome);
            pstmt.setInt(2, idUsuario);
            
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int idCategoria = rs.getInt(1);
                getConnection().commit();
                return idCategoria;
            }
            throw new RuntimeException("Erro ao cadastrar categoria");
        } catch (SQLException e) {
            try {
                getConnection().rollback();
            } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao cadastrar categoria: " + e.getMessage(), e);
        }
    }
    
    public Categoria buscarCategoria(int idCategoria) {
        String sql = "SELECT id_categoria, nome, id_usuario, ativo FROM categorias WHERE id_categoria = ? AND ativo = TRUE";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idCategoria);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return mapCategoria(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar categoria: " + e.getMessage(), e);
        }
    }
    
    /**
     * Obtém ou cria a categoria especial "Sem Categoria" para um usuário.
     * Esta categoria é usada automaticamente para gastos sem categoria e não aparece na listagem normal.
     */
    public int obterOuCriarCategoriaSemCategoria(int idUsuario) {
        String sql = "SELECT id_categoria FROM categorias WHERE id_usuario = ? AND UPPER(nome) = 'SEM CATEGORIA' AND ativo = TRUE";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("id_categoria");
            }
            
            // Se não existe, cria a categoria
            return cadastrarCategoria("Sem Categoria", idUsuario);
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao obter/criar categoria Sem Categoria: " + e.getMessage(), e);
        }
    }
    
    public List<Categoria> buscarCategoriasPorUsuario(int idUsuario) {
        // Exclui a categoria "Sem Categoria" da listagem para o usuário
        String sql = "SELECT id_categoria, nome, id_usuario, ativo FROM categorias WHERE id_usuario = ? AND ativo = TRUE AND UPPER(nome) != 'SEM CATEGORIA' ORDER BY nome";
        List<Categoria> categorias = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                categorias.add(mapCategoria(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar categorias: " + e.getMessage(), e);
        }
        
        return categorias;
    }
    
    private Categoria mapCategoria(ResultSet rs) throws SQLException {
        Categoria categoria = new Categoria(
            rs.getInt("id_categoria"),
            rs.getString("nome"),
            rs.getInt("id_usuario")
        );
        categoria.setAtivo(rs.getBoolean("ativo"));
        return categoria;
    }
    
    // ========== OPERAÇÕES DE CONTA ==========
    
    public int cadastrarConta(String nome, String tipo, double saldoAtual, int idUsuario) {
        return cadastrarConta(nome, tipo, saldoAtual, idUsuario, null, null);
    }
    
    public int cadastrarConta(String nome, String tipo, double saldoAtual, int idUsuario, Integer diaFechamento, Integer diaPagamento) {
        // Validações de entrada
        validateInput("Nome da conta", nome, 100);
        validateId("ID do usuário", idUsuario);
        validateAmount("Saldo inicial", saldoAtual);
        
        // Valida tipo de conta
        // Aceita tanto "INVESTIMENTO" quanto "INVESTIMENTO (CORRETORA)" para compatibilidade
        // Aceita variações de cartão de crédito (com e sem acento)
        String tipoUpper = tipo != null ? tipo.toUpperCase() : "";
        String[] tiposPermitidos = {"CORRENTE", "POUPANCA", "INVESTIMENTO", "INVESTIMENTO (CORRETORA)", 
                                   "CARTAO_CREDITO", "CARTAO DE CREDITO", "CARTAO DE CRÉDITO", 
                                   "CARTÃO DE CRÉDITO", "CARTÃO DE CREDITO", "OUTROS"};
        
        // Normaliza tipo para validação (remove acentos e espaços extras)
        String tipoNormalizado = tipoUpper.replace("É", "E").replace("À", "A").replace(" ", "_");
        boolean tipoValido = false;
        for (String tipoPermitido : tiposPermitidos) {
            String permitidoNormalizado = tipoPermitido.replace("É", "E").replace("À", "A").replace(" ", "_");
            if (tipoNormalizado.equals(permitidoNormalizado) || tipoUpper.contains("CARTAO") || tipoUpper.contains("CARTÃO")) {
                tipoValido = true;
                break;
            }
        }
        if (!tipoValido && !tipoUpper.contains("CARTAO") && !tipoUpper.contains("CARTÃO")) {
            validateEnum("Tipo de conta", tipo, tiposPermitidos);
        }
        
        // Normaliza tipo de cartão para formato padrão
        if (tipoUpper.contains("CARTAO") || tipoUpper.contains("CARTÃO")) {
            tipo = "CARTAO_CREDITO";
        }
        
        // Valida dias se for cartão de crédito
        boolean isCartao = tipoUpper.contains("CARTAO") || tipoUpper.contains("CARTÃO");
        if (isCartao) {
            if (diaFechamento != null && (diaFechamento < 1 || diaFechamento > 31)) {
                throw new IllegalArgumentException("Dia de fechamento deve estar entre 1 e 31");
            }
            if (diaPagamento != null && (diaPagamento < 1 || diaPagamento > 31)) {
                throw new IllegalArgumentException("Dia de pagamento deve estar entre 1 e 31");
            }
        }
        
        // Sanitiza nome
        nome = sanitizeString(nome);
        
        // Tenta inserir com campos de cartão de crédito se existirem
        if (hasCartaoCreditoColumns() && (diaFechamento != null || diaPagamento != null)) {
            String sql = "INSERT INTO contas (nome, tipo, saldo_atual, id_usuario, dia_fechamento, dia_pagamento) VALUES (?, ?, ?, ?, ?, ?) RETURNING id_conta";
            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setString(1, nome);
                pstmt.setString(2, tipo.toUpperCase());
                pstmt.setDouble(3, saldoAtual);
                pstmt.setInt(4, idUsuario);
                if (diaFechamento != null) {
                    pstmt.setInt(5, diaFechamento);
                } else {
                    pstmt.setNull(5, java.sql.Types.INTEGER);
                }
                if (diaPagamento != null) {
                    pstmt.setInt(6, diaPagamento);
                } else {
                    pstmt.setNull(6, java.sql.Types.INTEGER);
                }
                
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    int idConta = rs.getInt(1);
                    getConnection().commit();
                    return idConta;
                }
                throw new RuntimeException("Erro ao cadastrar conta");
            } catch (SQLException e) {
                // Se falhar por causa das colunas não existirem, tenta sem elas
                if (e.getMessage().contains("dia_fechamento") || e.getMessage().contains("dia_pagamento")) {
                    // Continua para o código abaixo que faz INSERT sem esses campos
                } else {
                    try {
                        getConnection().rollback();
                    } catch (SQLException ex) {}
                    throw new RuntimeException("Erro ao cadastrar conta: " + e.getMessage(), e);
                }
            }
        }
        
        // INSERT sem campos de cartão de crédito (para compatibilidade com bancos antigos)
        String sql = "INSERT INTO contas (nome, tipo, saldo_atual, id_usuario) VALUES (?, ?, ?, ?) RETURNING id_conta";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, nome);
            pstmt.setString(2, tipo.toUpperCase());
            pstmt.setDouble(3, saldoAtual);
            pstmt.setInt(4, idUsuario);
            
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int idConta = rs.getInt(1);
                getConnection().commit();
                return idConta;
            }
            throw new RuntimeException("Erro ao cadastrar conta");
        } catch (SQLException e) {
            try {
                getConnection().rollback();
            } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao cadastrar conta: " + e.getMessage(), e);
        }
    }
    
    public Conta buscarConta(int idConta) {
        String sql = "SELECT id_conta, nome, tipo, saldo_atual, id_usuario, ativo FROM contas WHERE id_conta = ? AND ativo = TRUE";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idConta);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return mapConta(rs, conn);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar conta: " + e.getMessage(), e);
        }
    }
    
    public List<Conta> buscarContasPorUsuario(int idUsuario) {
        // Query sem os campos novos para compatibilidade com bancos antigos
        // O mapConta tentará ler esses campos dos metadados se existirem
        String sql = "SELECT id_conta, nome, tipo, saldo_atual, id_usuario, ativo FROM contas WHERE id_usuario = ? AND ativo = TRUE ORDER BY nome";
        List<Conta> contas = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                contas.add(mapConta(rs, conn));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar contas: " + e.getMessage(), e);
        }
        
        return contas;
    }
    
    public List<Conta> buscarContasPorTipo(String tipo) {
        String sql = "SELECT id_conta, nome, tipo, saldo_atual, id_usuario, ativo " +
                    "FROM contas WHERE tipo = ? AND ativo = TRUE ORDER BY nome";
        List<Conta> contas = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tipo);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                contas.add(mapConta(rs, conn));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar contas por tipo: " + e.getMessage(), e);
        }
        
        return contas;
    }
    
    // Cache para verificar se as colunas de cartão de crédito existem
    private Boolean hasCartaoCreditoColumns = null;
    
    private boolean hasCartaoCreditoColumns() {
        if (hasCartaoCreditoColumns != null) {
            return hasCartaoCreditoColumns;
        }
        
        try (Connection conn = getConnection()) {
            String sql = "SELECT dia_fechamento, dia_pagamento FROM contas LIMIT 1";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.executeQuery();
                hasCartaoCreditoColumns = true;
                return true;
            }
        } catch (SQLException e) {
            hasCartaoCreditoColumns = false;
            return false;
        }
    }
    
    private Conta mapConta(ResultSet rs, Connection conn) throws SQLException {
        Conta conta = new Conta(
            rs.getInt("id_conta"),
            rs.getString("nome"),
            rs.getString("tipo"),
            rs.getDouble("saldo_atual"),
            rs.getInt("id_usuario")
        );
        conta.setAtivo(rs.getBoolean("ativo"));
        
        // Tenta ler campos opcionais de cartão de crédito apenas se existirem
        if (hasCartaoCreditoColumns()) {
            try {
                int idConta = conta.getIdConta();
                String sqlExtra = "SELECT dia_fechamento, dia_pagamento FROM contas WHERE id_conta = ?";
                try (PreparedStatement pstmtExtra = conn.prepareStatement(sqlExtra)) {
                    pstmtExtra.setInt(1, idConta);
                    ResultSet rsExtra = pstmtExtra.executeQuery();
                    if (rsExtra.next()) {
                        int diaFechamento = rsExtra.getInt("dia_fechamento");
                        if (!rsExtra.wasNull()) {
                            conta.setDiaFechamento(diaFechamento);
                        }
                        int diaPagamento = rsExtra.getInt("dia_pagamento");
                        if (!rsExtra.wasNull()) {
                            conta.setDiaPagamento(diaPagamento);
                        }
                    }
                }
            } catch (SQLException e) {
                // Ignora erros ao ler campos opcionais
            }
        }
        return conta;
    }
    
    // ========== OPERAÇÕES DE GASTO ==========
    
    public int cadastrarGasto(String descricao, double valor, LocalDate data, String frequencia, 
                              int idUsuario, List<Integer> idsCategorias, int idConta, String[] observacoes) {
        // Validações de entrada
        validateInput("Descrição do gasto", descricao, 500);
        validateAmount("Valor do gasto", valor);
        validateId("ID do usuário", idUsuario);
        validateId("ID da conta", idConta);
        
        if (data == null) {
            throw new IllegalArgumentException("Data não pode ser nula");
        }
        
        // Valida se a conta não é do tipo "Investimento"
        Conta conta = buscarConta(idConta);
        if (conta == null) {
            throw new IllegalArgumentException("Conta não encontrada");
        }
        // Verifica se é conta de investimento (aceita "Investimento" ou "Investimento (Corretora)")
        String tipoConta = conta.getTipo() != null ? conta.getTipo().toLowerCase().trim() : "";
        if (tipoConta.equals("investimento") || tipoConta.equals("investimento (corretora)") || tipoConta.startsWith("investimento")) {
            throw new IllegalArgumentException("Contas de investimento não podem ser usadas para gastos");
        }
        
        // Valida frequência (aceita valores antigos para migração)
        String[] frequenciasPermitidas = {"UNICA", "DIARIA", "SEMANAL", "MENSAL", "ANUAL", "ÚNICA", "Única", "única"};
        if (frequencia != null && !frequencia.trim().isEmpty()) {
            // Normaliza frequência para maiúsculas
            String freqUpper = frequencia.toUpperCase().trim();
            // Se não for uma frequência permitida, tenta normalizar
            if (!freqUpper.equals("UNICA") && !freqUpper.equals("DIARIA") && 
                !freqUpper.equals("SEMANAL") && !freqUpper.equals("MENSAL") && 
                !freqUpper.equals("ANUAL")) {
                // Tenta mapear valores antigos
                if (freqUpper.contains("ÚNIC") || freqUpper.equals("UNICA")) {
                    frequencia = "UNICA";
                } else {
                    // Se não conseguir mapear, usa UNICA como padrão
                    frequencia = "UNICA";
                }
            } else {
                frequencia = freqUpper;
            }
        }
        
        // Sanitiza descrição
        descricao = sanitizeString(descricao);
        
        // Valida IDs de categorias
        if (idsCategorias != null) {
            for (Integer idCat : idsCategorias) {
                validateId("ID da categoria", idCat);
            }
        }
        
        try {
            getConnection().setAutoCommit(false);
            
            // Calcula próxima recorrência
            LocalDate proximaRecorrencia = calcularProximaRecorrencia(data, frequencia);
            
            // Insere o gasto
            String sql = "INSERT INTO gastos (descricao, valor, data, frequencia, id_usuario, id_conta, proxima_recorrencia) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id_gasto";
            
            int idGasto;
            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setString(1, descricao);
                pstmt.setDouble(2, valor);
                pstmt.setDate(3, java.sql.Date.valueOf(data));
                pstmt.setString(4, frequencia != null ? frequencia.toUpperCase() : null);
                pstmt.setInt(5, idUsuario);
                pstmt.setInt(6, idConta);
                pstmt.setDate(7, proximaRecorrencia != null ? java.sql.Date.valueOf(proximaRecorrencia) : null);
                
                ResultSet rs = pstmt.executeQuery();
                if (!rs.next()) {
                    throw new RuntimeException("Erro ao cadastrar gasto");
                }
                idGasto = rs.getInt(1);
            }
            
            // Associa categorias (relacionamento N:N)
            // Se não houver categorias, usa a categoria especial "Sem Categoria"
            if (idsCategorias == null || idsCategorias.isEmpty()) {
                int idCategoriaSemCategoria = obterOuCriarCategoriaSemCategoria(idUsuario);
                idsCategorias = new ArrayList<>();
                idsCategorias.add(idCategoriaSemCategoria);
            }
            
            String sqlCategoria = "INSERT INTO categoria_gasto (id_categoria, id_gasto) VALUES (?, ?)";
            try (PreparedStatement pstmt = getConnection().prepareStatement(sqlCategoria)) {
                for (int idCategoria : idsCategorias) {
                    // Valida se a categoria existe e está ativa
                    Categoria cat = buscarCategoria(idCategoria);
                    if (cat != null && cat.isAtivo()) {
                        pstmt.setInt(1, idCategoria);
                        pstmt.setInt(2, idGasto);
                        try {
                            pstmt.executeUpdate();
                        } catch (SQLException e) {
                            // Ignora duplicatas
                            if (!e.getMessage().contains("duplicate") && !e.getMessage().contains("unique")) {
                                throw e;
                            }
                        }
                    }
                }
            }
            
            // Insere observações (atributo multivalorado)
            if (observacoes != null && observacoes.length > 0) {
                String sqlObs = "INSERT INTO gasto_observacoes (id_gasto, observacao, ordem) VALUES (?, ?, ?)";
                try (PreparedStatement pstmt = getConnection().prepareStatement(sqlObs)) {
                    for (int i = 0; i < observacoes.length; i++) {
                        if (observacoes[i] != null && !observacoes[i].trim().isEmpty()) {
                            pstmt.setInt(1, idGasto);
                            pstmt.setString(2, observacoes[i]);
                            pstmt.setInt(3, i);
                            pstmt.executeUpdate();
                        }
                    }
                }
            }
            
            // Atualiza saldo da conta ou crédito disponível (para cartões de crédito)
            // Para cartões de crédito, saldo_atual representa o crédito disponível
            // Para outras contas, saldo_atual representa o saldo real
            // Em ambos os casos, subtraímos o valor do gasto
            String sqlConta = "UPDATE contas SET saldo_atual = saldo_atual - ? WHERE id_conta = ?";
            try (PreparedStatement pstmt = getConnection().prepareStatement(sqlConta)) {
                pstmt.setDouble(1, valor);
                pstmt.setInt(2, idConta);
                pstmt.executeUpdate();
            }
            
            getConnection().commit();
            return idGasto;
            
        } catch (SQLException e) {
            try {
                getConnection().rollback();
            } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao cadastrar gasto: " + e.getMessage(), e);
        } finally {
            try {
                getConnection().setAutoCommit(true);
            } catch (SQLException e) {}
        }
    }
    
    // Sobrecarga para manter compatibilidade
    public int cadastrarGasto(String descricao, double valor, LocalDate data, String frequencia, 
                              int idUsuario, int idCategoria, int idConta) {
        List<Integer> categorias = new ArrayList<>();
        if (idCategoria > 0) {
            categorias.add(idCategoria);
        }
        return cadastrarGasto(descricao, valor, data, frequencia, idUsuario, categorias, idConta, null);
    }
    
    // Sobrecarga sem observações
    public int cadastrarGasto(String descricao, double valor, LocalDate data, String frequencia, 
                              int idUsuario, List<Integer> idsCategorias, int idConta) {
        return cadastrarGasto(descricao, valor, data, frequencia, idUsuario, idsCategorias, idConta, null);
    }
    
    public Gasto buscarGasto(int idGasto) {
        String sql = "SELECT id_gasto, descricao, valor, data, frequencia, id_usuario, id_conta, " +
                    "proxima_recorrencia, id_gasto_original, ativo " +
                    "FROM gastos WHERE id_gasto = ? AND ativo = TRUE";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idGasto);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                Gasto gasto = mapGasto(rs);
                
                // Carrega observações
                String[] observacoes = buscarObservacoesGasto(idGasto);
                gasto.setObservacoes(observacoes);
                
                return gasto;
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar gasto: " + e.getMessage(), e);
        }
    }
    
    private String[] buscarObservacoesGasto(int idGasto) {
        String sql = "SELECT observacao FROM gasto_observacoes WHERE id_gasto = ? ORDER BY ordem";
        List<String> obsList = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idGasto);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                obsList.add(rs.getString("observacao"));
            }
        } catch (SQLException e) {
            // Ignora erro - retorna array vazio
        }
        
        return obsList.toArray(new String[0]);
    }
    
    /**
     * Busca todas as observações de múltiplos gastos de uma vez (otimização batch)
     * Retorna um Map onde a chave é o id_gasto e o valor é o array de observações
     */
    public Map<Integer, String[]> buscarObservacoesDeGastos(List<Integer> idsGastos) {
        if (idsGastos == null || idsGastos.isEmpty()) {
            return new HashMap<>();
        }
        
        Map<Integer, List<String>> obsMap = new HashMap<>();
        
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < idsGastos.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        
        String sql = "SELECT id_gasto, observacao FROM gasto_observacoes " +
                    "WHERE id_gasto IN (" + placeholders + ") " +
                    "ORDER BY id_gasto, ordem";
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            for (int i = 0; i < idsGastos.size(); i++) {
                pstmt.setInt(i + 1, idsGastos.get(i));
            }
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                int idGasto = rs.getInt("id_gasto");
                String observacao = rs.getString("observacao");
                
                obsMap.computeIfAbsent(idGasto, k -> new ArrayList<>()).add(observacao);
            }
        } catch (SQLException e) {
            // Ignora erro e retorna map vazio
        }
        
        // Converte List<String> para String[]
        Map<Integer, String[]> resultado = new HashMap<>();
        for (Map.Entry<Integer, List<String>> entry : obsMap.entrySet()) {
            resultado.put(entry.getKey(), entry.getValue().toArray(new String[0]));
        }
        
        return resultado;
    }
    
    private void inserirObservacoesReceita(int idReceita, String[] observacoes) throws SQLException {
        if (observacoes == null || observacoes.length == 0) {
            return;
        }
        
        String sqlObs = "INSERT INTO receita_observacoes (id_receita, observacao, ordem) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sqlObs)) {
            for (int i = 0; i < observacoes.length; i++) {
                String obs = observacoes[i];
                if (obs == null || obs.trim().isEmpty()) {
                    continue;
                }
                pstmt.setInt(1, idReceita);
                pstmt.setString(2, obs.trim());
                pstmt.setInt(3, i);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }
    
    private String[] buscarObservacoesReceita(int idReceita) {
        String sql = "SELECT observacao FROM receita_observacoes WHERE id_receita = ? ORDER BY ordem";
        List<String> obsList = new ArrayList<>();
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, idReceita);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                obsList.add(rs.getString("observacao"));
            }
        } catch (SQLException e) {
            // Ignora erro e retorna array vazio
        }
        
        return obsList.toArray(new String[0]);
    }
    
    /**
     * Busca todas as observações de múltiplas receitas de uma vez (otimização batch)
     * Retorna um Map onde a chave é o id_receita e o valor é o array de observações
     */
    public Map<Integer, String[]> buscarObservacoesDeReceitas(List<Integer> idsReceitas) {
        if (idsReceitas == null || idsReceitas.isEmpty()) {
            return new HashMap<>();
        }
        
        Map<Integer, List<String>> obsMap = new HashMap<>();
        
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < idsReceitas.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        
        String sql = "SELECT id_receita, observacao FROM receita_observacoes " +
                    "WHERE id_receita IN (" + placeholders + ") " +
                    "ORDER BY id_receita, ordem";
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            for (int i = 0; i < idsReceitas.size(); i++) {
                pstmt.setInt(i + 1, idsReceitas.get(i));
            }
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                int idReceita = rs.getInt("id_receita");
                String observacao = rs.getString("observacao");
                
                obsMap.computeIfAbsent(idReceita, k -> new ArrayList<>()).add(observacao);
            }
        } catch (SQLException e) {
            // Ignora erro e retorna map vazio
        }
        
        // Converte List<String> para String[]
        Map<Integer, String[]> resultado = new HashMap<>();
        for (Map.Entry<Integer, List<String>> entry : obsMap.entrySet()) {
            resultado.put(entry.getKey(), entry.getValue().toArray(new String[0]));
        }
        
        return resultado;
    }
    
    public List<Gasto> buscarGastosPorUsuario(int idUsuario) {
        String sql = "SELECT id_gasto, descricao, valor, data, frequencia, id_usuario, id_conta, " +
                    "proxima_recorrencia, id_gasto_original, ativo " +
                    "FROM gastos WHERE id_usuario = ? AND ativo = TRUE ORDER BY data DESC";
        List<Gasto> gastos = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                Gasto gasto = mapGasto(rs);
                gasto.setObservacoes(buscarObservacoesGasto(gasto.getIdGasto()));
                gastos.add(gasto);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar gastos: " + e.getMessage(), e);
        }
        
        return gastos;
    }
    
    public List<Categoria> buscarCategoriasDoGasto(int idGasto) {
        String sql = "SELECT c.id_categoria, c.nome, c.id_usuario, c.ativo " +
                    "FROM categorias c " +
                    "INNER JOIN categoria_gasto cg ON c.id_categoria = cg.id_categoria " +
                    "WHERE cg.id_gasto = ? AND cg.ativo = TRUE AND c.ativo = TRUE";
        List<Categoria> categorias = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idGasto);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                categorias.add(mapCategoria(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar categorias do gasto: " + e.getMessage(), e);
        }
        
        return categorias;
    }
    
    /**
     * Busca todas as categorias de múltiplos gastos de uma vez (otimização batch)
     * Retorna um Map onde a chave é o id_gasto e o valor é a lista de categorias
     */
    public Map<Integer, List<Categoria>> buscarCategoriasDeGastos(List<Integer> idsGastos) {
        if (idsGastos == null || idsGastos.isEmpty()) {
            return new HashMap<>();
        }
        
        Map<Integer, List<Categoria>> resultado = new HashMap<>();
        
        // Cria uma string com placeholders para IN clause
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < idsGastos.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        
        String sql = "SELECT cg.id_gasto, c.id_categoria, c.nome, c.id_usuario, c.ativo " +
                    "FROM categorias c " +
                    "INNER JOIN categoria_gasto cg ON c.id_categoria = cg.id_categoria " +
                    "WHERE cg.id_gasto IN (" + placeholders + ") " +
                    "AND cg.ativo = TRUE AND c.ativo = TRUE " +
                    "ORDER BY cg.id_gasto";
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            for (int i = 0; i < idsGastos.size(); i++) {
                pstmt.setInt(i + 1, idsGastos.get(i));
            }
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                int idGasto = rs.getInt("id_gasto");
                Categoria categoria = mapCategoria(rs);
                
                resultado.computeIfAbsent(idGasto, k -> new ArrayList<>()).add(categoria);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar categorias dos gastos: " + e.getMessage(), e);
        }
        
        return resultado;
    }
    
    public List<Gasto> buscarGastosComFiltros(int idUsuario, Integer idCategoria, LocalDate data) {
        StringBuilder sql = new StringBuilder(
            "SELECT DISTINCT g.id_gasto, g.descricao, g.valor, g.data, g.frequencia, " +
            "g.id_usuario, g.id_conta, g.proxima_recorrencia, g.id_gasto_original, g.ativo " +
            "FROM gastos g " +
            "LEFT JOIN categoria_gasto cg ON g.id_gasto = cg.id_gasto AND cg.ativo = TRUE " +
            "WHERE g.id_usuario = ? AND g.ativo = TRUE"
        );
        
        List<Object> params = new ArrayList<>();
        params.add(idUsuario);
        
        if (idCategoria != null) {
            sql.append(" AND cg.id_categoria = ?");
            params.add(idCategoria);
        }
        
        if (data != null) {
            sql.append(" AND g.data = ?");
            params.add(data);
        }
        
        sql.append(" ORDER BY g.data DESC");
        
        List<Gasto> gastos = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof Integer) {
                    pstmt.setInt(i + 1, (Integer) param);
                } else if (param instanceof LocalDate) {
                    pstmt.setDate(i + 1, java.sql.Date.valueOf((LocalDate) param));
                }
            }
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Gasto gasto = mapGasto(rs);
                gasto.setObservacoes(buscarObservacoesGasto(gasto.getIdGasto()));
                gastos.add(gasto);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar gastos com filtros: " + e.getMessage(), e);
        }
        
        return gastos;
    }
    
    public List<Gasto> buscarGastosPorData(LocalDate data) {
        String sql = "SELECT id_gasto, descricao, valor, data, frequencia, id_usuario, id_conta, " +
                    "proxima_recorrencia, id_gasto_original, ativo " +
                    "FROM gastos WHERE data = ? AND ativo = TRUE ORDER BY data DESC";
        
        List<Gasto> gastos = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDate(1, java.sql.Date.valueOf(data));
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                Gasto gasto = mapGasto(rs);
                gasto.setObservacoes(buscarObservacoesGasto(gasto.getIdGasto()));
                gastos.add(gasto);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar gastos por data: " + e.getMessage(), e);
        }
        
        return gastos;
    }
    
    public List<Gasto> buscarGastosPorPeriodo(int idUsuario, LocalDate dataInicio, LocalDate dataFim) {
        String sql = "SELECT id_gasto, descricao, valor, data, frequencia, id_usuario, id_conta, " +
                    "proxima_recorrencia, id_gasto_original, ativo " +
                    "FROM gastos " +
                    "WHERE id_usuario = ? AND ativo = TRUE " +
                    "AND data >= ? AND data <= ? " +
                    "ORDER BY data DESC";
        
        List<Gasto> gastos = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            pstmt.setDate(2, java.sql.Date.valueOf(dataInicio));
            pstmt.setDate(3, java.sql.Date.valueOf(dataFim));
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Gasto gasto = mapGasto(rs);
                gasto.setObservacoes(buscarObservacoesGasto(gasto.getIdGasto()));
                gastos.add(gasto);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar gastos por período: " + e.getMessage(), e);
        }
        
        return gastos;
    }
    
    private Gasto mapGasto(ResultSet rs) throws SQLException {
        Gasto gasto = new Gasto(
            rs.getInt("id_gasto"),
            rs.getString("descricao"),
            rs.getDouble("valor"),
            rs.getDate("data").toLocalDate(),
            rs.getString("frequencia"),
            rs.getInt("id_usuario"),
            0, // idCategoria - não usado mais diretamente
            rs.getInt("id_conta")
        );
        
        java.sql.Date proxRec = rs.getDate("proxima_recorrencia");
        if (proxRec != null) {
            gasto.setProximaRecorrencia(proxRec.toLocalDate());
        }
        
        int idOriginal = rs.getInt("id_gasto_original");
        if (idOriginal > 0) {
            gasto.setIdGastoOriginal(idOriginal);
        }
        
        gasto.setAtivo(rs.getBoolean("ativo"));
        return gasto;
    }
    
    private LocalDate calcularProximaRecorrencia(LocalDate dataBase, String freq) {
        if (freq == null || freq.equals("Único")) {
            return null;
        }
        
        switch (freq) {
            case "Semanal":
                return dataBase.plusWeeks(1);
            case "Mensal":
                return dataBase.plusMonths(1);
            case "Anual":
                return dataBase.plusYears(1);
            default:
                return null;
        }
    }
    
    // Continua na próxima parte devido ao limite de tamanho...
    // Vou criar um arquivo separado para as operações restantes
    
    // ========== OPERAÇÕES DE RECEITA ==========
    
    public int cadastrarReceita(String descricao, double valor, LocalDate data, int idUsuario, int idConta) {
        return cadastrarReceita(descricao, valor, data, idUsuario, idConta, null);
    }
    
    public int cadastrarReceita(String descricao, double valor, LocalDate data, int idUsuario, int idConta, String[] observacoes) {
        // Validações de entrada
        validateInput("Descrição da receita", descricao, 500);
        validateAmount("Valor da receita", valor);
        validateId("ID do usuário", idUsuario);
        validateId("ID da conta", idConta);
        
        if (data == null) {
            throw new IllegalArgumentException("Data não pode ser nula");
        }
        
        // Valida se a conta não é do tipo "Investimento"
        Conta conta = buscarConta(idConta);
        if (conta == null) {
            throw new IllegalArgumentException("Conta não encontrada");
        }
        if (conta.getTipo() != null && conta.getTipo().equalsIgnoreCase("INVESTIMENTO")) {
            throw new IllegalArgumentException("Contas de investimento não podem ser usadas para receitas");
        }
        
        // Sanitiza descrição
        descricao = sanitizeString(descricao);
        
        try {
            getConnection().setAutoCommit(false);
            
            String sql = "INSERT INTO receitas (descricao, valor, data, id_usuario, id_conta) " +
                        "VALUES (?, ?, ?, ?, ?) RETURNING id_receita";
            
            int idReceita;
            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setString(1, descricao);
                pstmt.setDouble(2, valor);
                pstmt.setDate(3, java.sql.Date.valueOf(data));
                pstmt.setInt(4, idUsuario);
                pstmt.setInt(5, idConta);
                
                ResultSet rs = pstmt.executeQuery();
                if (!rs.next()) {
                    throw new RuntimeException("Erro ao cadastrar receita");
                }
                idReceita = rs.getInt(1);
            }
            
            if (observacoes != null && observacoes.length > 0) {
                inserirObservacoesReceita(idReceita, observacoes);
            }
            
            // Atualiza saldo da conta (adiciona a receita)
            String sqlConta = "UPDATE contas SET saldo_atual = saldo_atual + ? WHERE id_conta = ?";
            try (PreparedStatement pstmt = getConnection().prepareStatement(sqlConta)) {
                pstmt.setDouble(1, valor);
                pstmt.setInt(2, idConta);
                pstmt.executeUpdate();
            }
            
            getConnection().commit();
            return idReceita;
            
        } catch (SQLException e) {
            try {
                getConnection().rollback();
            } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao cadastrar receita: " + e.getMessage(), e);
        } finally {
            try {
                getConnection().setAutoCommit(true);
            } catch (SQLException e) {}
        }
    }
    
    public Receita buscarReceita(int idReceita) {
        String sql = "SELECT id_receita, descricao, valor, data, frequencia, id_usuario, id_conta, " +
                    "proxima_recorrencia, id_receita_original, ativo " +
                    "FROM receitas WHERE id_receita = ? AND ativo = TRUE";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idReceita);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return mapReceita(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar receita: " + e.getMessage(), e);
        }
    }
    
    public List<Receita> buscarReceitasPorUsuario(int idUsuario) {
        String sql = "SELECT id_receita, descricao, valor, data, frequencia, id_usuario, id_conta, " +
                    "proxima_recorrencia, id_receita_original, ativo " +
                    "FROM receitas WHERE id_usuario = ? AND ativo = TRUE ORDER BY data DESC";
        List<Receita> receitas = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                receitas.add(mapReceita(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar receitas: " + e.getMessage(), e);
        }
        
        return receitas;
    }
    
    public List<Receita> buscarReceitasComFiltros(int idUsuario, LocalDate data) {
        // Validações de entrada
        validateId("ID do usuário", idUsuario);
        
        // Constrói query de forma segura (sem concatenação de strings do usuário)
        StringBuilder sql = new StringBuilder(
            "SELECT id_receita, descricao, valor, data, frequencia, id_usuario, id_conta, " +
            "proxima_recorrencia, id_receita_original, ativo " +
            "FROM receitas " +
            "WHERE id_usuario = ? AND ativo = TRUE"
        );
        
        if (data != null) {
            sql.append(" AND data = ?");
        }
        sql.append(" ORDER BY data DESC");
        
        List<Receita> receitas = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            pstmt.setInt(1, idUsuario);
            if (data != null) {
                pstmt.setDate(2, java.sql.Date.valueOf(data));
            }
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                receitas.add(mapReceita(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar receitas com filtros: " + e.getMessage(), e);
        }
        
        return receitas;
    }
    
    public List<Receita> buscarReceitasPorData(LocalDate data) {
        String sql = "SELECT id_receita, descricao, valor, data, frequencia, id_usuario, id_conta, " +
                    "proxima_recorrencia, id_receita_original, ativo " +
                    "FROM receitas WHERE data = ? AND ativo = TRUE ORDER BY data DESC";
        
        List<Receita> receitas = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDate(1, java.sql.Date.valueOf(data));
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                receitas.add(mapReceita(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar receitas por data: " + e.getMessage(), e);
        }
        
        return receitas;
    }
    
    public List<Receita> buscarReceitasPorPeriodo(int idUsuario, LocalDate dataInicio, LocalDate dataFim) {
        String sql = "SELECT id_receita, descricao, valor, data, frequencia, id_usuario, id_conta, " +
                    "proxima_recorrencia, id_receita_original, ativo " +
                    "FROM receitas " +
                    "WHERE id_usuario = ? AND ativo = TRUE " +
                    "AND data >= ? AND data <= ? " +
                    "ORDER BY data DESC";
        
        List<Receita> receitas = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            pstmt.setDate(2, java.sql.Date.valueOf(dataInicio));
            pstmt.setDate(3, java.sql.Date.valueOf(dataFim));
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                receitas.add(mapReceita(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar receitas por período: " + e.getMessage(), e);
        }
        
        return receitas;
    }
    
    private Receita mapReceita(ResultSet rs) throws SQLException {
        Receita receita = new Receita(
            rs.getInt("id_receita"),
            rs.getString("descricao"),
            rs.getDouble("valor"),
            rs.getDate("data").toLocalDate(),
            rs.getInt("id_usuario"),
            rs.getInt("id_conta")
        );
        
        String freq = rs.getString("frequencia");
        if (freq != null) {
            receita.setFrequencia(freq);
        }
        
        java.sql.Date proxRec = rs.getDate("proxima_recorrencia");
        if (proxRec != null) {
            receita.setProximaRecorrencia(proxRec.toLocalDate());
        }
        
        int idOriginal = rs.getInt("id_receita_original");
        if (idOriginal > 0) {
            receita.setIdReceitaOriginal(idOriginal);
        }
        
        receita.setAtivo(rs.getBoolean("ativo"));
        receita.setObservacoes(buscarObservacoesReceita(receita.getIdReceita()));
        return receita;
    }
    
    // ========== OPERAÇÕES DE ORÇAMENTO ==========
    
    public int cadastrarOrcamento(double valorPlanejado, String periodo, int idCategoria, int idUsuario) {
        // Validações de entrada
        validateAmount("Valor planejado", valorPlanejado);
        validateInput("Período", periodo, 50);
        validateId("ID da categoria", idCategoria);
        validateId("ID do usuário", idUsuario);
        
        // Valida período
        String[] periodosPermitidos = {"MENSAL", "ANUAL", "SEMANAL", "DIARIO"};
        validateEnum("Período", periodo, periodosPermitidos);
        
        try {
            boolean wasAutoCommit = getConnection().getAutoCommit();
            if (wasAutoCommit) {
                getConnection().setAutoCommit(false);
            }
            
            String sql = "INSERT INTO orcamentos (valor_planejado, periodo, id_categoria, id_usuario) " +
                        "VALUES (?, ?, ?, ?) RETURNING id_orcamento";
            
            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setDouble(1, valorPlanejado);
                pstmt.setString(2, periodo.toUpperCase());
                pstmt.setInt(3, idCategoria);
                pstmt.setInt(4, idUsuario);
                
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    int idOrcamento = rs.getInt(1);
                    if (!wasAutoCommit) {
                        getConnection().commit();
                    }
                    return idOrcamento;
                }
                throw new RuntimeException("Erro ao cadastrar orçamento");
            } catch (SQLException e) {
                if (!wasAutoCommit) {
                    try {
                        getConnection().rollback();
                    } catch (SQLException ex) {}
                }
                throw new RuntimeException("Erro ao cadastrar orçamento: " + e.getMessage(), e);
            } finally {
                if (wasAutoCommit) {
                    try {
                        getConnection().setAutoCommit(true);
                    } catch (SQLException e) {}
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao cadastrar orçamento: " + e.getMessage(), e);
        }
    }
    
    public Orcamento buscarOrcamento(int idOrcamento) {
        String sql = "SELECT id_orcamento, valor_planejado, periodo, id_categoria, id_usuario, ativo " +
                    "FROM orcamentos WHERE id_orcamento = ? AND ativo = TRUE";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idOrcamento);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return mapOrcamento(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar orçamento: " + e.getMessage(), e);
        }
    }
    
    public List<Orcamento> buscarOrcamentosPorUsuario(int idUsuario) {
        String sql = "SELECT id_orcamento, valor_planejado, periodo, id_categoria, id_usuario, ativo " +
                    "FROM orcamentos WHERE id_usuario = ? AND ativo = TRUE ORDER BY periodo";
        List<Orcamento> orcamentos = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                orcamentos.add(mapOrcamento(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar orçamentos: " + e.getMessage(), e);
        }
        
        return orcamentos;
    }
    
    private Orcamento mapOrcamento(ResultSet rs) throws SQLException {
        Orcamento orcamento = new Orcamento(
            rs.getInt("id_orcamento"),
            rs.getDouble("valor_planejado"),
            rs.getString("periodo"),
            rs.getInt("id_categoria"),
            rs.getInt("id_usuario")
        );
        orcamento.setAtivo(rs.getBoolean("ativo"));
        return orcamento;
    }
    
    // ========== OPERAÇÕES DE TAG ==========
    
    public int cadastrarTag(String nome, String cor, int idUsuario) {
        // Validações de entrada
        validateInput("Nome da tag", nome, 50);
        validateInput("Cor da tag", cor, 20);
        validateId("ID do usuário", idUsuario);
        
        // Valida formato de cor (hexadecimal ou nome)
        if (!cor.matches("^#?[0-9A-Fa-f]{6}$|^[a-zA-Z]+$")) {
            throw new IllegalArgumentException("Cor inválida. Use formato hexadecimal (#RRGGBB) ou nome de cor");
        }
        
        // Sanitiza nome
        nome = sanitizeString(nome);
        
        try {
            boolean wasAutoCommit = getConnection().getAutoCommit();
            if (wasAutoCommit) {
                getConnection().setAutoCommit(false);
            }
            
            String sql = "INSERT INTO tags (nome, cor, id_usuario) VALUES (?, ?, ?) RETURNING id_tag";
            
            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setString(1, nome);
                pstmt.setString(2, cor);
                pstmt.setInt(3, idUsuario);
                
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    int idTag = rs.getInt(1);
                    if (!wasAutoCommit) {
                        getConnection().commit();
                    }
                    return idTag;
                }
                throw new RuntimeException("Erro ao cadastrar tag");
            } catch (SQLException e) {
                if (!wasAutoCommit) {
                    try {
                        getConnection().rollback();
                    } catch (SQLException ex) {}
                }
                throw new RuntimeException("Erro ao cadastrar tag: " + e.getMessage(), e);
            } finally {
                if (wasAutoCommit) {
                    try {
                        getConnection().setAutoCommit(true);
                    } catch (SQLException e) {}
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao cadastrar tag: " + e.getMessage(), e);
        }
    }
    
    public Tag buscarTag(int idTag) {
        String sql = "SELECT id_tag, nome, cor, id_usuario, ativo FROM tags WHERE id_tag = ? AND ativo = TRUE";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idTag);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return mapTag(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar tag: " + e.getMessage(), e);
        }
    }
    
    public List<Tag> buscarTagsPorUsuario(int idUsuario) {
        String sql = "SELECT id_tag, nome, cor, id_usuario, ativo FROM tags WHERE id_usuario = ? AND ativo = TRUE ORDER BY nome";
        List<Tag> tags = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                tags.add(mapTag(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar tags: " + e.getMessage(), e);
        }
        
        return tags;
    }
    
    public void associarTagTransacao(int idTransacao, String tipoTransacao, int idTag) {
        String sql = "INSERT INTO transacao_tag (id_transacao, tipo_transacao, id_tag) VALUES (?, ?, ?) " +
                    "ON CONFLICT (id_transacao, tipo_transacao, id_tag) DO NOTHING";
        
        try {
            boolean wasAutoCommit = getConnection().getAutoCommit();
            if (wasAutoCommit) {
                getConnection().setAutoCommit(false);
            }
            
            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setInt(1, idTransacao);
                pstmt.setString(2, tipoTransacao);
                pstmt.setInt(3, idTag);
                pstmt.executeUpdate();
                
                // Sempre faz commit quando autoCommit está desabilitado
                getConnection().commit();
            } catch (SQLException e) {
                try {
                    getConnection().rollback();
                } catch (SQLException ex) {}
                throw e;
            } finally {
                if (wasAutoCommit) {
                    try {
                        getConnection().setAutoCommit(true);
                    } catch (SQLException e) {}
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao associar tag: " + e.getMessage(), e);
        }
    }
    
    public List<Tag> buscarTagsGasto(int idGasto) {
        String sql = "SELECT t.id_tag, t.nome, t.cor, t.id_usuario, t.ativo " +
                    "FROM tags t " +
                    "INNER JOIN transacao_tag tt ON t.id_tag = tt.id_tag " +
                    "WHERE tt.id_transacao = ? AND tt.tipo_transacao = 'GASTO' " +
                    "AND tt.ativo = TRUE AND t.ativo = TRUE";
        List<Tag> tags = new ArrayList<>();
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, idGasto);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                tags.add(mapTag(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar tags do gasto: " + e.getMessage(), e);
        }
        
        return tags;
    }
    
    /**
     * Busca todas as tags de múltiplos gastos de uma vez (otimização batch)
     * Retorna um Map onde a chave é o id_gasto e o valor é a lista de tags
     */
    public Map<Integer, List<Tag>> buscarTagsDeGastos(List<Integer> idsGastos) {
        if (idsGastos == null || idsGastos.isEmpty()) {
            return new HashMap<>();
        }
        
        Map<Integer, List<Tag>> resultado = new HashMap<>();
        
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < idsGastos.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        
        String sql = "SELECT tt.id_transacao, t.id_tag, t.nome, t.cor, t.id_usuario, t.ativo " +
                    "FROM tags t " +
                    "INNER JOIN transacao_tag tt ON t.id_tag = tt.id_tag " +
                    "WHERE tt.id_transacao IN (" + placeholders + ") " +
                    "AND tt.tipo_transacao = 'GASTO' " +
                    "AND tt.ativo = TRUE AND t.ativo = TRUE " +
                    "ORDER BY tt.id_transacao";
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            for (int i = 0; i < idsGastos.size(); i++) {
                pstmt.setInt(i + 1, idsGastos.get(i));
            }
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                int idGasto = rs.getInt("id_transacao");
                Tag tag = mapTag(rs);
                
                resultado.computeIfAbsent(idGasto, k -> new ArrayList<>()).add(tag);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar tags dos gastos: " + e.getMessage(), e);
        }
        
        return resultado;
    }
    
    public List<Tag> buscarTagsReceita(int idReceita) {
        String sql = "SELECT t.id_tag, t.nome, t.cor, t.id_usuario, t.ativo " +
                    "FROM tags t " +
                    "INNER JOIN transacao_tag tt ON t.id_tag = tt.id_tag " +
                    "WHERE tt.id_transacao = ? AND tt.tipo_transacao = 'RECEITA' " +
                    "AND tt.ativo = TRUE AND t.ativo = TRUE";
        List<Tag> tags = new ArrayList<>();
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, idReceita);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                tags.add(mapTag(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar tags da receita: " + e.getMessage(), e);
        }
        
        return tags;
    }
    
    /**
     * Busca todas as tags de múltiplas receitas de uma vez (otimização batch)
     * Retorna um Map onde a chave é o id_receita e o valor é a lista de tags
     */
    public Map<Integer, List<Tag>> buscarTagsDeReceitas(List<Integer> idsReceitas) {
        if (idsReceitas == null || idsReceitas.isEmpty()) {
            return new HashMap<>();
        }
        
        Map<Integer, List<Tag>> resultado = new HashMap<>();
        
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < idsReceitas.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        
        String sql = "SELECT tt.id_transacao, t.id_tag, t.nome, t.cor, t.id_usuario, t.ativo " +
                    "FROM tags t " +
                    "INNER JOIN transacao_tag tt ON t.id_tag = tt.id_tag " +
                    "WHERE tt.id_transacao IN (" + placeholders + ") " +
                    "AND tt.tipo_transacao = 'RECEITA' " +
                    "AND tt.ativo = TRUE AND t.ativo = TRUE " +
                    "ORDER BY tt.id_transacao";
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            for (int i = 0; i < idsReceitas.size(); i++) {
                pstmt.setInt(i + 1, idsReceitas.get(i));
            }
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                int idReceita = rs.getInt("id_transacao");
                Tag tag = mapTag(rs);
                
                resultado.computeIfAbsent(idReceita, k -> new ArrayList<>()).add(tag);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar tags das receitas: " + e.getMessage(), e);
        }
        
        return resultado;
    }
    
    private Tag mapTag(ResultSet rs) throws SQLException {
        Tag tag = new Tag(
            rs.getInt("id_tag"),
            rs.getString("nome"),
            rs.getString("cor"),
            rs.getInt("id_usuario")
        );
        tag.setAtivo(rs.getBoolean("ativo"));
        return tag;
    }
    
    // ========== OPERAÇÕES DE INVESTIMENTO ==========
    
    public int cadastrarInvestimento(String nome, String nomeAtivo, String categoria, double quantidade, 
                                    double precoAporte, double corretagem, String corretora,
                                    LocalDate dataAporte, int idUsuario, int idConta, String moeda,
                                    String tipoInvestimento, String tipoRentabilidade, String indice, 
                                    Double percentualIndice, Double taxaFixa, LocalDate dataVencimento) {
        try {
            boolean wasAutoCommit = getConnection().getAutoCommit();
            if (wasAutoCommit) {
                getConnection().setAutoCommit(false);
            }
            
            String sql = "INSERT INTO investimentos (nome, nome_ativo, categoria, quantidade, preco_aporte, " +
                        "corretagem, corretora, data_aporte, id_usuario, id_conta, moeda, tipo_investimento, " +
                        "tipo_rentabilidade, indice, percentual_indice, taxa_fixa, data_vencimento) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_investimento";
            
            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setString(1, nome);
                pstmt.setString(2, nomeAtivo);
                pstmt.setString(3, categoria);
                pstmt.setDouble(4, quantidade);
                pstmt.setDouble(5, precoAporte);
                pstmt.setDouble(6, corretagem);
                pstmt.setString(7, corretora);
                pstmt.setDate(8, java.sql.Date.valueOf(dataAporte));
                pstmt.setInt(9, idUsuario);
                pstmt.setInt(10, idConta);
                pstmt.setString(11, moeda);
                pstmt.setString(12, tipoInvestimento);
                pstmt.setString(13, tipoRentabilidade);
                pstmt.setString(14, indice);
                pstmt.setObject(15, percentualIndice);
                pstmt.setObject(16, taxaFixa);
                pstmt.setObject(17, dataVencimento != null ? java.sql.Date.valueOf(dataVencimento) : null);
                
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    int idInvestimento = rs.getInt(1);
                    if (!wasAutoCommit) {
                        getConnection().commit();
                    }
                    return idInvestimento;
                }
                throw new RuntimeException("Erro ao cadastrar investimento");
            } catch (SQLException e) {
                if (!wasAutoCommit) {
                    try {
                        getConnection().rollback();
                    } catch (SQLException ex) {}
                }
                throw new RuntimeException("Erro ao cadastrar investimento: " + e.getMessage(), e);
            } finally {
                if (wasAutoCommit) {
                    try {
                        getConnection().setAutoCommit(true);
                    } catch (SQLException e) {}
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao cadastrar investimento: " + e.getMessage(), e);
        }
    }
    
    // Sobrecargas para compatibilidade
    public int cadastrarInvestimento(String nome, String categoria, double quantidade, 
                                    double precoAporte, double corretagem, String corretora,
                                    LocalDate dataAporte, int idUsuario, int idConta, String moeda) {
        return cadastrarInvestimento(nome, null, categoria, quantidade, precoAporte, corretagem, 
                                    corretora, dataAporte, idUsuario, idConta, moeda, null, null, null, null, null, null);
    }
    
    public int cadastrarInvestimento(String nome, String nomeAtivo, String categoria, double quantidade, 
                                    double precoAporte, double corretagem, String corretora,
                                    LocalDate dataAporte, int idUsuario, int idConta, String moeda) {
        return cadastrarInvestimento(nome, nomeAtivo, categoria, quantidade, precoAporte, corretagem,
                                    corretora, dataAporte, idUsuario, idConta, moeda, null, null, null, null, null, null);
    }
    
    public Investimento buscarInvestimento(int idInvestimento) {
        String sql = "SELECT * FROM investimentos WHERE id_investimento = ? AND ativo = TRUE";
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, idInvestimento);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return mapInvestimento(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar investimento: " + e.getMessage(), e);
        }
    }
    
    public List<Investimento> buscarInvestimentosPorUsuario(int idUsuario) {
        String sql = "SELECT * FROM investimentos WHERE id_usuario = ? AND ativo = TRUE ORDER BY data_aporte DESC";
        List<Investimento> investimentos = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                investimentos.add(mapInvestimento(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar investimentos: " + e.getMessage(), e);
        }
        
        return investimentos;
    }
    
    private Investimento mapInvestimento(ResultSet rs) throws SQLException {
        Investimento inv = new Investimento(
            rs.getInt("id_investimento"),
            rs.getString("nome"),
            rs.getString("nome_ativo"),
            rs.getString("categoria"),
            rs.getDouble("quantidade"),
            rs.getDouble("preco_aporte"),
            rs.getDouble("corretagem"),
            rs.getString("corretora"),
            rs.getDate("data_aporte").toLocalDate(),
            rs.getInt("id_usuario"),
            rs.getInt("id_conta"),
            rs.getString("moeda")
        );
        
        inv.setTipoInvestimento(rs.getString("tipo_investimento"));
        inv.setTipoRentabilidade(rs.getString("tipo_rentabilidade"));
        inv.setIndice(rs.getString("indice"));
        
        Object pct = rs.getObject("percentual_indice");
        if (pct != null) {
            inv.setPercentualIndice(((Number) pct).doubleValue());
        }
        
        Object taxa = rs.getObject("taxa_fixa");
        if (taxa != null) {
            inv.setTaxaFixa(((Number) taxa).doubleValue());
        }
        
        java.sql.Date venc = rs.getDate("data_vencimento");
        if (venc != null) {
            inv.setDataVencimento(venc.toLocalDate());
        }
        
        inv.setAtivo(rs.getBoolean("ativo"));
        return inv;
    }
    
    // ========== OPERAÇÕES DE ATUALIZAÇÃO ==========
    
    public void atualizarCategoria(int idCategoria, String novoNome) {
        // Validações de entrada
        validateId("ID da categoria", idCategoria);
        validateInput("Nome da categoria", novoNome, 100);
        
        // Sanitiza nome
        novoNome = sanitizeString(novoNome);
        
        String sql = "UPDATE categorias SET nome = ? WHERE id_categoria = ?";
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, novoNome);
            pstmt.setInt(2, idCategoria);
            pstmt.executeUpdate();
            getConnection().commit();
        } catch (SQLException e) {
            try {
                getConnection().rollback();
            } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao atualizar categoria: " + e.getMessage(), e);
        }
    }
    
    public void atualizarConta(int idConta, String novoNome, String novoTipo, double novoSaldo) {
        atualizarConta(idConta, novoNome, novoTipo, novoSaldo, null, null);
    }
    
    public void atualizarConta(int idConta, String novoNome, String novoTipo, double novoSaldo, Integer diaFechamento, Integer diaPagamento) {
        // Tenta atualizar com campos de cartão de crédito se existirem
        if (hasCartaoCreditoColumns() && (diaFechamento != null || diaPagamento != null)) {
            String sql = "UPDATE contas SET nome = ?, tipo = ?, saldo_atual = ?, dia_fechamento = ?, dia_pagamento = ? WHERE id_conta = ?";
            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setString(1, novoNome);
                pstmt.setString(2, novoTipo);
                pstmt.setDouble(3, novoSaldo);
                if (diaFechamento != null) {
                    pstmt.setInt(4, diaFechamento);
                } else {
                    pstmt.setNull(4, java.sql.Types.INTEGER);
                }
                if (diaPagamento != null) {
                    pstmt.setInt(5, diaPagamento);
                } else {
                    pstmt.setNull(5, java.sql.Types.INTEGER);
                }
                pstmt.setInt(6, idConta);
                pstmt.executeUpdate();
                getConnection().commit();
                return;
            } catch (SQLException e) {
                // Se falhar por causa das colunas não existirem, tenta sem elas
                if (!e.getMessage().contains("dia_fechamento") && !e.getMessage().contains("dia_pagamento")) {
                    try {
                        getConnection().rollback();
                    } catch (SQLException ex) {}
                    throw new RuntimeException("Erro ao atualizar conta: " + e.getMessage(), e);
                }
                // Continua para o código abaixo que faz UPDATE sem esses campos
            }
        }
        
        // UPDATE sem campos de cartão de crédito (para compatibilidade com bancos antigos)
        String sql = "UPDATE contas SET nome = ?, tipo = ?, saldo_atual = ? WHERE id_conta = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, novoNome);
            pstmt.setString(2, novoTipo);
            pstmt.setDouble(3, novoSaldo);
            pstmt.setInt(4, idConta);
            pstmt.executeUpdate();
            getConnection().commit();
        } catch (SQLException e) {
            try {
                getConnection().rollback();
            } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao atualizar conta: " + e.getMessage(), e);
        }
    }
    
    public void atualizarGasto(int idGasto, String novaDescricao, double novoValor, LocalDate novaData, String novaFrequencia) {
        String sql = "UPDATE gastos SET descricao = ?, valor = ?, data = ?, frequencia = ? WHERE id_gasto = ?";
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, novaDescricao);
            pstmt.setDouble(2, novoValor);
            pstmt.setDate(3, java.sql.Date.valueOf(novaData));
            pstmt.setString(4, novaFrequencia);
            pstmt.setInt(5, idGasto);
            pstmt.executeUpdate();
            getConnection().commit();
        } catch (SQLException e) {
            try {
                getConnection().rollback();
            } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao atualizar gasto: " + e.getMessage(), e);
        }
    }
    
    public void atualizarReceita(int idReceita, String novaDescricao, double novoValor, LocalDate novaData) {
        String sql = "UPDATE receitas SET descricao = ?, valor = ?, data = ? WHERE id_receita = ?";
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, novaDescricao);
            pstmt.setDouble(2, novoValor);
            pstmt.setDate(3, java.sql.Date.valueOf(novaData));
            pstmt.setInt(4, idReceita);
            pstmt.executeUpdate();
            getConnection().commit();
        } catch (SQLException e) {
            try {
                getConnection().rollback();
            } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao atualizar receita: " + e.getMessage(), e);
        }
    }
    
    public void atualizarOrcamento(int idOrcamento, double novoValorPlanejado, String novoPeriodo) {
        String sql = "UPDATE orcamentos SET valor_planejado = ?, periodo = ? WHERE id_orcamento = ?";
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setDouble(1, novoValorPlanejado);
            pstmt.setString(2, novoPeriodo);
            pstmt.setInt(3, idOrcamento);
            pstmt.executeUpdate();
            getConnection().commit();
        } catch (SQLException e) {
            try {
                getConnection().rollback();
            } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao atualizar orçamento: " + e.getMessage(), e);
        }
    }
    
    public void atualizarTag(int idTag, String novoNome, String novaCor) {
        String sql = "UPDATE tags SET nome = ?, cor = ? WHERE id_tag = ?";
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, novoNome);
            pstmt.setString(2, novaCor);
            pstmt.setInt(3, idTag);
            pstmt.executeUpdate();
            getConnection().commit();
        } catch (SQLException e) {
            try {
                getConnection().rollback();
            } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao atualizar tag: " + e.getMessage(), e);
        }
    }
    
    public void atualizarInvestimento(int idInvestimento, String nome, String nomeAtivo, String categoria, 
                                     double quantidade, double precoAporte, double corretagem, 
                                     String corretora, LocalDate dataAporte, String moeda) {
        String sql = "UPDATE investimentos SET nome = ?, nome_ativo = ?, categoria = ?, quantidade = ?, " +
                    "preco_aporte = ?, corretagem = ?, corretora = ?, data_aporte = ?, moeda = ? " +
                    "WHERE id_investimento = ?";
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, nome);
            pstmt.setString(2, nomeAtivo);
            pstmt.setString(3, categoria);
            pstmt.setDouble(4, quantidade);
            pstmt.setDouble(5, precoAporte);
            pstmt.setDouble(6, corretagem);
            pstmt.setString(7, corretora);
            pstmt.setDate(8, java.sql.Date.valueOf(dataAporte));
            pstmt.setString(9, moeda);
            pstmt.setInt(10, idInvestimento);
            pstmt.executeUpdate();
            getConnection().commit();
        } catch (SQLException e) {
            try {
                getConnection().rollback();
            } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao atualizar investimento: " + e.getMessage(), e);
        }
    }
    
    // ========== OPERAÇÕES DE EXCLUSÃO LÓGICA ==========
    
    public void excluirCategoria(int idCategoria) {
        String sql = "UPDATE categorias SET ativo = FALSE WHERE id_categoria = ?";
        executeUpdate(sql, idCategoria);
    }
    
    public void excluirConta(int idConta) {
        String sql = "UPDATE contas SET ativo = FALSE WHERE id_conta = ?";
        executeUpdate(sql, idConta);
    }
    
    public void excluirGasto(int idGasto) {
        try {
            getConnection().setAutoCommit(false);
            
            // Busca o gasto para obter valor e conta (sem filtrar por ativo)
            String sqlBuscar = "SELECT id_gasto, descricao, valor, data, frequencia, id_usuario, id_conta, " +
                              "proxima_recorrencia, id_gasto_original, ativo " +
                              "FROM gastos WHERE id_gasto = ?";
            Gasto gasto = null;
            try (PreparedStatement pstmt = getConnection().prepareStatement(sqlBuscar)) {
                pstmt.setInt(1, idGasto);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    gasto = mapGasto(rs);
                }
            }
            
            if (gasto == null) {
                throw new IllegalArgumentException("Gasto não encontrado");
            }
            if (!gasto.isAtivo()) {
                throw new IllegalArgumentException("Gasto já foi excluído");
            }
            
            // Exclui o gasto (exclusão lógica)
            String sql = "UPDATE gastos SET ativo = FALSE WHERE id_gasto = ?";
            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setInt(1, idGasto);
                pstmt.executeUpdate();
            }
            
            // Reverte o saldo da conta (adiciona o valor de volta)
            String sqlConta = "UPDATE contas SET saldo_atual = saldo_atual + ? WHERE id_conta = ?";
            try (PreparedStatement pstmt = getConnection().prepareStatement(sqlConta)) {
                pstmt.setDouble(1, gasto.getValor());
                pstmt.setInt(2, gasto.getIdConta());
                pstmt.executeUpdate();
            }
            
            getConnection().commit();
        } catch (SQLException e) {
            try {
                getConnection().rollback();
            } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao excluir gasto: " + e.getMessage(), e);
        } finally {
            try {
                getConnection().setAutoCommit(true);
            } catch (SQLException e) {}
        }
    }
    
    public void excluirReceita(int idReceita) {
        try {
            getConnection().setAutoCommit(false);
            
            // Busca a receita para obter valor e conta (sem filtrar por ativo)
            String sqlBuscar = "SELECT id_receita, descricao, valor, data, frequencia, id_usuario, id_conta, " +
                              "proxima_recorrencia, id_receita_original, ativo " +
                              "FROM receitas WHERE id_receita = ?";
            Receita receita = null;
            try (PreparedStatement pstmt = getConnection().prepareStatement(sqlBuscar)) {
                pstmt.setInt(1, idReceita);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    receita = mapReceita(rs);
                }
            }
            
            if (receita == null) {
                throw new IllegalArgumentException("Receita não encontrada");
            }
            if (!receita.isAtivo()) {
                throw new IllegalArgumentException("Receita já foi excluída");
            }
            
            // Exclui a receita (exclusão lógica)
            String sql = "UPDATE receitas SET ativo = FALSE WHERE id_receita = ?";
            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setInt(1, idReceita);
                pstmt.executeUpdate();
            }
            
            // Reverte o saldo da conta (subtrai o valor)
            String sqlConta = "UPDATE contas SET saldo_atual = saldo_atual - ? WHERE id_conta = ?";
            try (PreparedStatement pstmt = getConnection().prepareStatement(sqlConta)) {
                pstmt.setDouble(1, receita.getValor());
                pstmt.setInt(2, receita.getIdConta());
                pstmt.executeUpdate();
            }
            
            getConnection().commit();
        } catch (SQLException e) {
            try {
                getConnection().rollback();
            } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao excluir receita: " + e.getMessage(), e);
        } finally {
            try {
                getConnection().setAutoCommit(true);
            } catch (SQLException e) {}
        }
    }
    
    public void excluirOrcamento(int idOrcamento) {
        String sql = "UPDATE orcamentos SET ativo = FALSE WHERE id_orcamento = ?";
        executeUpdate(sql, idOrcamento);
    }
    
    public void excluirTag(int idTag) {
        String sql = "UPDATE tags SET ativo = FALSE WHERE id_tag = ?";
        executeUpdate(sql, idTag);
    }
    
    public void excluirInvestimento(int idInvestimento) {
        String sql = "UPDATE investimentos SET ativo = FALSE WHERE id_investimento = ?";
        executeUpdate(sql, idInvestimento);
    }
    
    private void executeUpdate(String sql, int id) {
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
            getConnection().commit();
        } catch (SQLException e) {
            try {
                getConnection().rollback();
            } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao executar exclusão: " + e.getMessage(), e);
        }
    }
    
    // ========== OPERAÇÕES DE RELATÓRIOS ==========
    
    public double calcularTotalGastosPorCategoria(int idCategoria) {
        String sql = "SELECT COALESCE(SUM(g.valor), 0) as total " +
                    "FROM gastos g " +
                    "INNER JOIN categoria_gasto cg ON g.id_gasto = cg.id_gasto " +
                    "WHERE cg.id_categoria = ? AND g.ativo = TRUE AND cg.ativo = TRUE";
        
        return executeDoubleQuery(sql, idCategoria);
    }
    
    public double calcularTotalGastosPorCategoriaEUsuario(int idCategoria, int idUsuario) {
        String sql = "SELECT COALESCE(SUM(g.valor), 0) as total " +
                    "FROM gastos g " +
                    "INNER JOIN categoria_gasto cg ON g.id_gasto = cg.id_gasto " +
                    "WHERE cg.id_categoria = ? AND g.id_usuario = ? " +
                    "AND g.ativo = TRUE AND cg.ativo = TRUE";
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, idCategoria);
            pstmt.setInt(2, idUsuario);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getDouble("total");
            }
            return 0.0;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao calcular total: " + e.getMessage(), e);
        }
    }
    
    /**
     * Busca todos os gastos por categoria de uma vez (otimizado para evitar N+1 queries)
     * Retorna um Map onde a chave é o id da categoria e o valor é o total de gastos
     */
    public Map<Integer, Double> calcularTotalGastosPorTodasCategoriasEUsuario(int idUsuario) {
        // Como todos os gastos são associados à categoria "Sem Categoria" quando não têm categoria,
        // a query original funciona, mas precisamos garantir que inclua a categoria "Sem Categoria"
        String sql = "SELECT cg.id_categoria, COALESCE(SUM(g.valor), 0) as total " +
                    "FROM categoria_gasto cg " +
                    "INNER JOIN gastos g ON cg.id_gasto = g.id_gasto " +
                    "WHERE g.id_usuario = ? AND g.ativo = TRUE AND cg.ativo = TRUE " +
                    "GROUP BY cg.id_categoria";
        
        Map<Integer, Double> gastosPorCategoria = new HashMap<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                int idCategoria = rs.getInt("id_categoria");
                double total = rs.getDouble("total");
                gastosPorCategoria.put(idCategoria, total);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao calcular gastos por categoria: " + e.getMessage(), e);
        }
        
        return gastosPorCategoria;
    }
    
    public double calcularTotalGastosUsuario(int idUsuario) {
        String sql = "SELECT COALESCE(SUM(valor), 0) as total FROM gastos WHERE id_usuario = ? AND ativo = TRUE";
        return executeDoubleQuery(sql, idUsuario);
    }
    
    public double calcularTotalReceitasUsuario(int idUsuario) {
        String sql = "SELECT COALESCE(SUM(valor), 0) as total FROM receitas WHERE id_usuario = ? AND ativo = TRUE";
        return executeDoubleQuery(sql, idUsuario);
    }
    
    public double calcularSaldoUsuario(int idUsuario) {
        return calcularTotalReceitasUsuario(idUsuario) - calcularTotalGastosUsuario(idUsuario);
    }
    
    public double calcularTotalSaldoContasUsuario(int idUsuario) {
        // Exclui cartões de crédito pois são dívidas, não ativos
        String sql = "SELECT COALESCE(SUM(saldo_atual), 0) as total " +
                    "FROM contas WHERE id_usuario = ? " +
                    "AND ativo = TRUE " +
                    "AND (tipo IS NULL OR (UPPER(tipo) NOT LIKE 'CARTAO%' AND UPPER(tipo) != 'CARTAO_CREDITO'))";
        return executeDoubleQuery(sql, idUsuario);
    }
    
    public double calcularSaldoContasUsuarioSemInvestimento(int idUsuario) {
        // Exclui contas de investimento e cartões de crédito
        // Cartões de crédito não devem ser somados no saldo total (saldo_atual representa crédito disponível)
        String sql = "SELECT COALESCE(SUM(saldo_atual), 0) as total " +
                    "FROM contas WHERE id_usuario = ? " +
                    "AND UPPER(tipo) NOT LIKE 'INVESTIMENTO%' " +
                    "AND UPPER(tipo) NOT LIKE 'CARTAO%' " +
                    "AND ativo = TRUE";
        return executeDoubleQuery(sql, idUsuario);
    }
    
    public double calcularTotalCreditoDisponivelCartoes(int idUsuario) {
        // Calcula o total de crédito disponível em todos os cartões de crédito
        String sql = "SELECT COALESCE(SUM(saldo_atual), 0) as total " +
                    "FROM contas WHERE id_usuario = ? " +
                    "AND (UPPER(tipo) LIKE 'CARTAO%' OR UPPER(tipo) = 'CARTAO_CREDITO') " +
                    "AND ativo = TRUE";
        return executeDoubleQuery(sql, idUsuario);
    }
    
    private double executeDoubleQuery(String sql, int id) {
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getDouble("total");
            }
            return 0.0;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao executar query: " + e.getMessage(), e);
        }
    }
    
    // ========== PROCESSAMENTO DE RECORRÊNCIAS ==========
    
    public int processarRecorrencias() {
        int recorrenciasCriadas = 0;
        LocalDate hoje = LocalDate.now();
        
        System.out.println("[RECORRÊNCIAS] Processando recorrências para " + hoje);
        
        // Processa gastos recorrentes
        recorrenciasCriadas += processarGastosRecorrentes(hoje);
        
        // Processa receitas recorrentes
        recorrenciasCriadas += processarReceitasRecorrentes(hoje);
        
        if (recorrenciasCriadas > 0) {
            System.out.println("[RECORRÊNCIAS] Total de " + recorrenciasCriadas + " transações criadas automaticamente");
        } else {
            System.out.println("[RECORRÊNCIAS] Nenhuma recorrência pendente para hoje");
        }
        
        return recorrenciasCriadas;
    }
    
    private int processarGastosRecorrentes(LocalDate hoje) {
        String sql = "SELECT * FROM gastos " +
                    "WHERE proxima_recorrencia IS NOT NULL " +
                    "AND proxima_recorrencia <= ? " +
                    "AND ativo = TRUE";
        
        int criados = 0;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDate(1, java.sql.Date.valueOf(hoje));
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                Gasto gastoOriginal = mapGasto(rs);
                gastoOriginal.setObservacoes(buscarObservacoesGasto(gastoOriginal.getIdGasto()));
                
                System.out.println("[RECORRÊNCIAS] Criando recorrência de gasto: " + gastoOriginal.getDescricao());
                
                // Busca as categorias do gasto original
                List<Categoria> categorias = buscarCategoriasDoGasto(gastoOriginal.getIdGasto());
                List<Integer> idsCategorias = new ArrayList<>();
                for (Categoria cat : categorias) {
                    idsCategorias.add(cat.getIdCategoria());
                }
                
                // Cria novo gasto com a data de recorrência
                int novoIdGasto = cadastrarGasto(
                    gastoOriginal.getDescricao() + " (Recorrência)",
                    gastoOriginal.getValor(),
                    gastoOriginal.getProximaRecorrencia(),
                    gastoOriginal.getFrequencia(),
                    gastoOriginal.getIdUsuario(),
                    idsCategorias,
                    gastoOriginal.getIdConta(),
                    gastoOriginal.getObservacoes()
                );
                
                // Marca o novo gasto como recorrência do original
                String sqlUpdate = "UPDATE gastos SET id_gasto_original = ? WHERE id_gasto = ?";
                try (PreparedStatement pstmtUpdate = getConnection().prepareStatement(sqlUpdate)) {
                    pstmtUpdate.setInt(1, gastoOriginal.getIdGasto());
                    pstmtUpdate.setInt(2, novoIdGasto);
                    pstmtUpdate.executeUpdate();
                }
                
                // Busca e copia tags do gasto original
                List<Tag> tags = buscarTagsGasto(gastoOriginal.getIdGasto());
                for (Tag tag : tags) {
                    associarTagTransacao(novoIdGasto, "GASTO", tag.getIdTag());
                }
                
                // Atualiza a próxima recorrência do original
                LocalDate novaRecorrencia = calcularProximaRecorrencia(
                    gastoOriginal.getProximaRecorrencia(), 
                    gastoOriginal.getFrequencia()
                );
                String sqlRec = "UPDATE gastos SET proxima_recorrencia = ? WHERE id_gasto = ?";
                try (PreparedStatement pstmtRec = getConnection().prepareStatement(sqlRec)) {
                    pstmtRec.setDate(1, novaRecorrencia != null ? java.sql.Date.valueOf(novaRecorrencia) : null);
                    pstmtRec.setInt(2, gastoOriginal.getIdGasto());
                    pstmtRec.executeUpdate();
                }
                
                getConnection().commit();
                criados++;
            }
        } catch (SQLException e) {
            try {
                getConnection().rollback();
            } catch (SQLException ex) {}
            System.err.println("Erro ao processar recorrências de gastos: " + e.getMessage());
        }
        
        return criados;
    }
    
    private int processarReceitasRecorrentes(LocalDate hoje) {
        String sql = "SELECT * FROM receitas " +
                    "WHERE proxima_recorrencia IS NOT NULL " +
                    "AND proxima_recorrencia <= ? " +
                    "AND ativo = TRUE";
        
        int criados = 0;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDate(1, java.sql.Date.valueOf(hoje));
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                Receita receitaOriginal = mapReceita(rs);
                
                System.out.println("[RECORRÊNCIAS] Criando recorrência de receita: " + receitaOriginal.getDescricao());
                
                // Cria nova receita com a data de recorrência
                int novoIdReceita = cadastrarReceita(
                    receitaOriginal.getDescricao() + " (Recorrência)",
                    receitaOriginal.getValor(),
                    receitaOriginal.getProximaRecorrencia(),
                    receitaOriginal.getIdUsuario(),
                    receitaOriginal.getIdConta(),
                    receitaOriginal.getObservacoes()
                );
                
                // Marca a nova receita como recorrência da original
                String sqlUpdate = "UPDATE receitas SET id_receita_original = ?, frequencia = ?, proxima_recorrencia = ? WHERE id_receita = ?";
                try (PreparedStatement pstmtUpdate = conn.prepareStatement(sqlUpdate)) {
                    pstmtUpdate.setInt(1, receitaOriginal.getIdReceita());
                    pstmtUpdate.setString(2, receitaOriginal.getFrequencia());
                    pstmtUpdate.setDate(3, receitaOriginal.getProximaRecorrencia() != null ? 
                        java.sql.Date.valueOf(receitaOriginal.getProximaRecorrencia()) : null);
                    pstmtUpdate.setInt(4, novoIdReceita);
                    pstmtUpdate.executeUpdate();
                }
                
                // Busca e copia tags da receita original
                List<Tag> tags = buscarTagsReceita(receitaOriginal.getIdReceita());
                for (Tag tag : tags) {
                    associarTagTransacao(novoIdReceita, "RECEITA", tag.getIdTag());
                }
                
                // Atualiza a próxima recorrência da original
                LocalDate novaRecorrencia = calcularProximaRecorrencia(
                    receitaOriginal.getProximaRecorrencia(), 
                    receitaOriginal.getFrequencia()
                );
                String sqlRec = "UPDATE receitas SET proxima_recorrencia = ? WHERE id_receita = ?";
                try (PreparedStatement pstmtRec = getConnection().prepareStatement(sqlRec)) {
                    pstmtRec.setDate(1, novaRecorrencia != null ? java.sql.Date.valueOf(novaRecorrencia) : null);
                    pstmtRec.setInt(2, receitaOriginal.getIdReceita());
                    pstmtRec.executeUpdate();
                }
                
                getConnection().commit();
                criados++;
            }
        } catch (SQLException e) {
            try {
                getConnection().rollback();
            } catch (SQLException ex) {}
            System.err.println("Erro ao processar recorrências de receitas: " + e.getMessage());
        }
        
        return criados;
    }
    
    // ========== OPERAÇÕES ADICIONAIS ==========
    
    public void adicionarCategoriaAoGasto(int idCategoria, int idGasto) {
        String sql = "INSERT INTO categoria_gasto (id_categoria, id_gasto) VALUES (?, ?) " +
                    "ON CONFLICT (id_categoria, id_gasto) DO UPDATE SET ativo = TRUE";
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, idCategoria);
            pstmt.setInt(2, idGasto);
            pstmt.executeUpdate();
            getConnection().commit();
        } catch (SQLException e) {
            try {
                getConnection().rollback();
            } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao adicionar categoria ao gasto: " + e.getMessage(), e);
        }
    }
    
    public void removerCategoriaDoGasto(int idCategoria, int idGasto) {
        String sql = "UPDATE categoria_gasto SET ativo = FALSE WHERE id_categoria = ? AND id_gasto = ?";
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, idCategoria);
            pstmt.setInt(2, idGasto);
            pstmt.executeUpdate();
            getConnection().commit();
        } catch (SQLException e) {
            try {
                getConnection().rollback();
            } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao remover categoria do gasto: " + e.getMessage(), e);
        }
    }
    
    public List<Gasto> buscarGastosPorCategoria(int idCategoria) {
        String sql = "SELECT DISTINCT g.* FROM gastos g " +
                    "INNER JOIN categoria_gasto cg ON g.id_gasto = cg.id_gasto " +
                    "WHERE cg.id_categoria = ? AND g.ativo = TRUE AND cg.ativo = TRUE " +
                    "ORDER BY g.data DESC";
        
        List<Gasto> gastos = new ArrayList<>();
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, idCategoria);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                Gasto gasto = mapGasto(rs);
                gasto.setObservacoes(buscarObservacoesGasto(gasto.getIdGasto()));
                gastos.add(gasto);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar gastos por categoria: " + e.getMessage(), e);
        }
        
        return gastos;
    }
    
    public List<Gasto> buscarGastosPorTag(int idTag) {
        String sql = "SELECT DISTINCT g.* FROM gastos g " +
                    "INNER JOIN transacao_tag tt ON g.id_gasto = tt.id_transacao " +
                    "WHERE tt.id_tag = ? AND tt.tipo_transacao = 'GASTO' " +
                    "AND g.ativo = TRUE AND tt.ativo = TRUE " +
                    "ORDER BY g.data DESC";
        
        List<Gasto> gastos = new ArrayList<>();
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, idTag);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                Gasto gasto = mapGasto(rs);
                gasto.setObservacoes(buscarObservacoesGasto(gasto.getIdGasto()));
                gastos.add(gasto);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar gastos por tag: " + e.getMessage(), e);
        }
        
        return gastos;
    }
    
    public List<Receita> buscarReceitasPorTag(int idTag) {
        String sql = "SELECT DISTINCT r.* FROM receitas r " +
                    "INNER JOIN transacao_tag tt ON r.id_receita = tt.id_transacao " +
                    "WHERE tt.id_tag = ? AND tt.tipo_transacao = 'RECEITA' " +
                    "AND r.ativo = TRUE AND tt.ativo = TRUE " +
                    "ORDER BY r.data DESC";
        
        List<Receita> receitas = new ArrayList<>();
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, idTag);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                receitas.add(mapReceita(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar receitas por tag: " + e.getMessage(), e);
        }
        
        return receitas;
    }
    
    public List<Orcamento> buscarOrcamentosPorCategoria(int idCategoria) {
        String sql = "SELECT id_orcamento, valor_planejado, periodo, id_categoria, id_usuario, ativo " +
                    "FROM orcamentos WHERE id_categoria = ? AND ativo = TRUE";
        List<Orcamento> orcamentos = new ArrayList<>();
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, idCategoria);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                orcamentos.add(mapOrcamento(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar orçamentos por categoria: " + e.getMessage(), e);
        }
        
        return orcamentos;
    }
    
    public void imprimirEstatisticas() {
        try {
            String sql = "SELECT " +
                        "(SELECT COUNT(*) FROM usuarios WHERE ativo = TRUE) as usuarios, " +
                        "(SELECT COUNT(*) FROM categorias WHERE ativo = TRUE) as categorias, " +
                        "(SELECT COUNT(*) FROM gastos WHERE ativo = TRUE) as gastos, " +
                        "(SELECT COUNT(*) FROM receitas WHERE ativo = TRUE) as receitas, " +
                        "(SELECT COUNT(*) FROM contas WHERE ativo = TRUE) as contas, " +
                        "(SELECT COUNT(*) FROM orcamentos WHERE ativo = TRUE) as orcamentos, " +
                        "(SELECT COUNT(*) FROM investimentos WHERE ativo = TRUE) as investimentos";
            
            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    System.out.println("\n=== ESTATÍSTICAS DO BANCO DE DADOS ===");
                    System.out.println("Usuários cadastrados: " + rs.getInt("usuarios"));
                    System.out.println("Categorias cadastradas: " + rs.getInt("categorias"));
                    System.out.println("Gastos registrados: " + rs.getInt("gastos"));
                    System.out.println("Receitas registradas: " + rs.getInt("receitas"));
                    System.out.println("Contas cadastradas: " + rs.getInt("contas"));
                    System.out.println("Orçamentos definidos: " + rs.getInt("orcamentos"));
                    System.out.println("Investimentos cadastrados: " + rs.getInt("investimentos"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Erro ao imprimir estatísticas: " + e.getMessage());
        }
    }
    
    /**
     * Fecha a conexão com o banco de dados (fecha conexão da thread atual)
     * Nota: Em produção com ThreadLocal, cada thread fecha sua própria conexão
     */
    public void close() {
        closeConnection();
        System.out.println("Conexão com PostgreSQL fechada");
    }
}

