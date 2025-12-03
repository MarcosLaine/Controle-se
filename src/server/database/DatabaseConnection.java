package server.database;

import java.sql.Connection;
import java.sql.SQLException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.IOException;

public class DatabaseConnection {
    private static HikariDataSource dataSource;
    private static DatabaseConnection instance;

    private DatabaseConnection() {
        initializeDataSource();
    }

    public static synchronized DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    private void initializeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            return;
        }

        String host = System.getenv("PGHOST");
        String port = System.getenv("PGPORT");
        String database = System.getenv("PGDATABASE");
        String username = System.getenv("PGUSER");
        String password = System.getenv("PGPASSWORD");
        String sslMode = System.getenv("PGSSLMODE");

        if (host == null || host.isEmpty()) {
             // Fallback to properties file logic if needed, or just simpler logic
             // Copying logic from original class for robustness
             try (FileInputStream input = new FileInputStream("db.properties")) {
                Properties prop = new Properties();
                prop.load(input);
                
                if (host == null) host = prop.getProperty("db.host");
                if (port == null) port = prop.getProperty("db.port");
                if (database == null) database = prop.getProperty("db.database");
                if (username == null) username = prop.getProperty("db.username");
                if (password == null) password = prop.getProperty("db.password");
                if (sslMode == null) sslMode = prop.getProperty("db.sslmode");
            } catch (IOException ex) {
                // Ignore if file doesn't exist, assume env vars or defaults
            }
        }

        // Defaults
        if (host == null) host = "localhost";
        if (port == null) port = "5432";
        if (database == null) database = "controlese";
        if (username == null) username = "postgres";
        if (password == null) password = "postgres";
        if (sslMode == null) sslMode = "require";

        // Configurações adicionais na URL para melhorar estabilidade da conexão
        String jdbcUrl = String.format(
            "jdbc:postgresql://%s:%s/%s?sslmode=%s&socketTimeout=30&tcpKeepAlive=true&connectTimeout=10",
            host, port, database, sslMode
        );

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        
        // Optimizations for limited resources (e.g. free tier database)
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(2);
        
        // Timeouts mais robustos para evitar conexões quebradas
        config.setConnectionTimeout(30000); // 30 seconds (aumentado de 20s)
        config.setValidationTimeout(5000); // 5 seconds para validar conexão
        config.setIdleTimeout(600000); // 10 minutes (aumentado de 5min)
        config.setMaxLifetime(2700000); // 45 minutes (aumentado de 30min, menor que timeout do banco)
        
        // Validação de conexão antes de usar
        config.setConnectionTestQuery("SELECT 1");
        
        // Detecção de vazamento de conexões (útil para debug)
        config.setLeakDetectionThreshold(60000); // 60 segundos
        
        // Configurações adicionais para estabilidade
        config.setRegisterMbeans(true); // Permite monitoramento via JMX
        config.setPoolName("ControleSePool");
        
        dataSource = new HikariDataSource(config);
    }

    public Connection getConnection() throws SQLException {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            // Se o pool estiver fechado ou houver erro crítico, tenta reinicializar
            if (dataSource == null || dataSource.isClosed()) {
                synchronized (this) {
                    if (dataSource == null || dataSource.isClosed()) {
                        initializeDataSource();
                        return dataSource.getConnection();
                    }
                }
            }
            throw e;
        }
    }
    
    /**
     * Reinicializa o pool de conexões (útil quando há problemas de conexão)
     */
    public synchronized void reconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            try {
                dataSource.close();
            } catch (Exception e) {
                // Ignora erros ao fechar
            }
        }
        dataSource = null;
        initializeDataSource();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public static void shutdown() {
        if (instance != null) {
            instance.close();
        }
    }
}

