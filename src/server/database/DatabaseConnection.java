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

        String jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s?sslmode=%s", host, port, database, sslMode);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        
        // Optimizations for limited resources (e.g. free tier database)
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(2);
        config.setIdleTimeout(300000); // 5 minutes
        config.setConnectionTimeout(20000); // 20 seconds
        config.setMaxLifetime(1800000); // 30 minutes
        
        dataSource = new HikariDataSource(config);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
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

