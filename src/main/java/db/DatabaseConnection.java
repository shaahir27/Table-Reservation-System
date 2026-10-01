package db;

import exception.DatabaseUnavailableException;
import util.AppLog;
import util.OracleErrorMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Manages database connectivity to Oracle.
 * Produces independent connections per request for safe transaction boundaries.
 */
public class DatabaseConnection {
    private static final Properties properties = new Properties();
    private static final String DEFAULT_URL = "jdbc:oracle:thin:@//localhost:1521/XEPDB1";
    private static final String DEFAULT_USER = "rrs_user";
    private static final String DEFAULT_PASSWORD = "change_me";

    static {
        DriverManager.setLoginTimeout(5);
        loadConfiguration();
    }

    private static void loadConfiguration() {
        // 1. Check for .env file in the working directory
        File envFile = new File(".env");
        if (envFile.exists()) {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(envFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eqIndex = line.indexOf('=');
                    if (eqIndex > 0) {
                        String key = line.substring(0, eqIndex).trim();
                        String val = line.substring(eqIndex + 1).trim();
                        if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                            val = val.substring(1, val.length() - 1);
                        }
                        if ("DB_URL".equalsIgnoreCase(key) || "db.url".equalsIgnoreCase(key)) {
                            properties.setProperty("db.url", val);
                        } else if ("DB_USERNAME".equalsIgnoreCase(key) || "DB_USER".equalsIgnoreCase(key) || "db.username".equalsIgnoreCase(key)) {
                            properties.setProperty("db.username", val);
                        } else if ("DB_PASSWORD".equalsIgnoreCase(key) || "DB_PASS".equalsIgnoreCase(key) || "db.password".equalsIgnoreCase(key)) {
                            properties.setProperty("db.password", val);
                        }
                    }
                }
                AppLog.info("Loaded database configuration from .env file");
            } catch (IOException e) {
                AppLog.warning("Could not read .env file: " + e.getMessage());
            }
        }

        // 2. Check for config.properties in working directory
        File configFile = new File("config.properties");
        if (configFile.exists()) {
            try (InputStream in = new FileInputStream(configFile)) {
                Properties prop = new Properties();
                prop.load(in);
                for (String name : prop.stringPropertyNames()) {
                    if (!properties.containsKey(name)) {
                        properties.setProperty(name, prop.getProperty(name));
                    }
                }
                AppLog.info("Loaded configuration from working directory: " + configFile.getAbsolutePath());
            } catch (IOException e) {
                AppLog.warning("Could not read config.properties from working directory: " + e.getMessage());
            }
        }

        // 3. Fallback to classpath config.properties
        try (InputStream in = DatabaseConnection.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (in != null) {
                Properties prop = new Properties();
                prop.load(in);
                for (String name : prop.stringPropertyNames()) {
                    if (!properties.containsKey(name)) {
                        properties.setProperty(name, prop.getProperty(name));
                    }
                }
                AppLog.info("Loaded configuration from classpath resource config.properties");
            }
        } catch (IOException e) {
            AppLog.warning("Could not read config.properties from classpath: " + e.getMessage());
        }

        // 4. Default fallbacks if still unconfigured
        if (!properties.containsKey("db.url")) properties.setProperty("db.url", DEFAULT_URL);
        if (!properties.containsKey("db.username")) properties.setProperty("db.username", DEFAULT_USER);
        if (!properties.containsKey("db.password")) properties.setProperty("db.password", DEFAULT_PASSWORD);
    }

    /**
     * Obtains a new JDBC connection. Callers must close it (e.g. via try-with-resources).
     */
    public static Connection getConnection() {
        String url = properties.getProperty("db.url", DEFAULT_URL);
        String user = properties.getProperty("db.username", DEFAULT_USER);
        String pass = properties.getProperty("db.password", DEFAULT_PASSWORD);

        try {
            return DriverManager.getConnection(url, user, pass);
        } catch (SQLException e) {
            throw (DatabaseUnavailableException) OracleErrorMapper.map(e, "DatabaseConnection.getConnection");
        }
    }

    /**
     * Verifies that the database is reachable and credentials are valid.
     * Throws DatabaseUnavailableException on failure.
     */
    public static void testConnection() {
        try (Connection con = getConnection()) {
            if (con == null || con.isClosed()) {
                throw new DatabaseUnavailableException("Failed to establish a valid Oracle connection.");
            }
            AppLog.info("Database connection test succeeded.");
        } catch (SQLException e) {
            throw (DatabaseUnavailableException) OracleErrorMapper.map(e, "DatabaseConnection.testConnection");
        }
    }

    public static String getDbUrl() {
        return properties.getProperty("db.url", DEFAULT_URL);
    }

    public static String getDbUsername() {
        return properties.getProperty("db.username", DEFAULT_USER);
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
