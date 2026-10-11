package db;

import exception.DatabaseUnavailableException;
import util.AppLog;
import util.OracleErrorMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Manages database connectivity to Oracle.
 * Produces independent connections per request for safe transaction boundaries.
 * All connection details (URL, username, password) are loaded from .env.
 */
public class DatabaseConnection {
    private static final Properties properties = new Properties();

    static {
        DriverManager.setLoginTimeout(5);
        loadConfiguration();
    }

    private static void loadConfiguration() {
        // 1. Load from .env if present
        File envFile = new File(".env");
        if (envFile.exists()) {
            try (InputStream in = new FileInputStream(envFile)) {
                Properties envProps = new Properties();
                envProps.load(in);
                for (String key : envProps.stringPropertyNames()) {
                    String val = envProps.getProperty(key).trim();
                    if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                        val = val.substring(1, val.length() - 1);
                    }
                    properties.setProperty(key.toUpperCase(), val);
                }
                AppLog.info("Loaded database configuration from .env");
            } catch (Exception e) {
                AppLog.warning("Could not read .env file: " + e.getMessage());
            }
        }

        // 2. Fallback to config.properties if present
        File configFile = new File("config.properties");
        if (configFile.exists()) {
            try (InputStream in = new FileInputStream(configFile)) {
                Properties configProps = new Properties();
                configProps.load(in);
                for (String key : configProps.stringPropertyNames()) {
                    properties.putIfAbsent(key.toUpperCase(), configProps.getProperty(key).trim());
                }
                AppLog.info("Loaded database configuration from config.properties");
            } catch (Exception e) {
                AppLog.warning("Could not read config.properties: " + e.getMessage());
            }
        }
    }

    private static String getSetting(String... keys) {
        for (String key : keys) {
            String val = properties.getProperty(key.toUpperCase());
            if (val != null && !val.trim().isEmpty()) {
                return val.trim();
            }
            val = System.getenv(key);
            if (val != null && !val.trim().isEmpty()) {
                return val.trim();
            }
        }
        return null;
    }

    /**
     * Obtains a new JDBC connection. Callers must close it (e.g. via try-with-resources).
     */
    public static Connection getConnection() {
        String url = getSetting("DB_URL", "db.url");
        String user = getSetting("DB_USERNAME", "DB_USER", "db.username");
        String pass = getSetting("DB_PASSWORD", "DB_PASS", "db.password");

        if (url == null) {
            throw new DatabaseUnavailableException(
                "Database URL is missing. Please set DB_URL in your .env file."
            );
        }
        if (user == null || pass == null) {
            throw new DatabaseUnavailableException(
                "Database credentials are missing. Please set DB_USERNAME and DB_PASSWORD in your .env file."
            );
        }

        try {
            return DriverManager.getConnection(url, user, pass);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "DatabaseConnection.getConnection");
        }
    }

    /**
     * Verifies that the database is reachable and credentials are valid.
     * Throws AppException on failure.
     */
    public static void testConnection() {
        try (Connection con = getConnection()) {
            if (con == null || con.isClosed()) {
                throw new DatabaseUnavailableException("Failed to establish a valid Oracle connection.");
            }
            AppLog.info("Database connection test succeeded.");
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "DatabaseConnection.testConnection");
        }
    }

    public static String getDbUrl() {
        String url = getSetting("DB_URL", "db.url");
        return url != null ? url : "(unconfigured)";
    }

    public static String getDbUsername() {
        String user = getSetting("DB_USERNAME", "DB_USER", "db.username");
        return user != null ? user : "(unconfigured)";
    }

    public static void main(String[] args) {
        System.out.println("Testing Oracle database connection...");
        System.out.println("URL: " + getDbUrl());
        System.out.println("Username: " + getDbUsername());
        try {
            testConnection();
            System.out.println("SUCCESS: Connection to Oracle database established successfully!");
        } catch (Exception e) {
            System.err.println("FAILED: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Cause: " + e.getCause().getMessage());
            }
        }
    }
}
