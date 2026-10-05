package com.registry.storesapp;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DBConnection {

    private static String jdbcUrl;
    private static String dbUser;
    private static String decryptedPassword;

    static {
        Properties props = new Properties();

        // Securely load database configuration definitions from the resources folder
        try (InputStream input = DBConnection.class.getResourceAsStream("/com/registry/storesapp/db.properties")) {
            if (input == null) {
                throw new RuntimeException("CRITICAL CONFIG ERROR: Unable to find 'db.properties' inside the resources package.");
            }
            props.load(input);

            // Fetch parameter assignments
            jdbcUrl = props.getProperty("db.url");
            dbUser = props.getProperty("db.user");
            String encryptedPass = props.getProperty("db.password.encrypted");

            // Smart-Check: Decrypt only if wrapped in ENC(...), otherwise fallback gracefully to raw text
            if (encryptedPass != null && encryptedPass.startsWith("ENC(") && encryptedPass.endsWith(")")) {
                try {
                    // Strip out the ENC() wrapper wrapper and decrypt
                    String cleanHash = encryptedPass.substring(4, encryptedPass.length() - 1);
                    decryptedPassword = CryptoUtil.decrypt(cleanHash);
                } catch (Exception e) {
                    System.err.println("WARNING: Encryption key mismatch detected. Falling back to local password text.");
                    decryptedPassword = "dreamatic@77";
                }
            } else {
                // No ENC wrapper present, use the string directly out of properties
                decryptedPassword = encryptedPass != null ? encryptedPass : "dreamatic@77";
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("CRITICAL: Application terminated. Failed to load or parse configuration environment profiles.");
        }
    }

    public static Connection getConnection() throws SQLException {
        try {
            // Explicitly verify the presence of the MySQL JDBC driver context mapping
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver missing from classpath settings.");
        }
        return DriverManager.getConnection(jdbcUrl, dbUser, decryptedPassword);
    }
}