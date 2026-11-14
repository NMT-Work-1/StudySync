package com.studysync.database;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    // Adjustable JDBC configuration (change localhost to course server host if needed)
    private static final String DB_URL = "jdbc:mysql://localhost:3306/studysync";
    private static final String DB_USER = "root";
    private static final String DEFAULT_PASSWORD = "your_password";
    private static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName(JDBC_DRIVER);
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver not found: " + e.getMessage(), e);
        }

        return DriverManager.getConnection(DB_URL, DB_USER, resolvePassword());
    }

    private static String resolvePassword() {
        // 1. Check ~/.db_password file first (matches ~/.gemini_key setup)
        try {
            Path passwordPath = Paths.get(System.getProperty("user.home"), ".db_password");
            if (Files.exists(passwordPath)) {
                return Files.readString(passwordPath, StandardCharsets.UTF_8).trim();
            }
        } catch (IOException ignored) {
        }

        // 2. Check DB_PASSWORD environment variable
        String envPassword = System.getenv("DB_PASSWORD");
        if (envPassword != null && !envPassword.trim().isEmpty()) {
            return envPassword.trim();
        }

        // 3. Fallback to constant for local testing
        return DEFAULT_PASSWORD;
    }
}
