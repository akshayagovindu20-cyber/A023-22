package com.eventbooking.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class Database {
    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/distributed_event_booking?serverTimezone=UTC";

    private Database() {
    }

    public static Connection getConnection() throws SQLException {
        String username = System.getenv("DB_USER");
        if (username == null || username.isBlank()) {
            throw new SQLException("DB_USER is not set. Configure the MySQL username before starting the server.");
        }
        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", System.getenv().getOrDefault("DB_PASSWORD", ""));
        properties.setProperty("useSSL", System.getenv().getOrDefault("DB_USE_SSL", "false"));
        properties.setProperty("allowPublicKeyRetrieval", "true");
        properties.setProperty("serverTimezone", "UTC");
        return DriverManager.getConnection(System.getenv().getOrDefault("DB_URL", DEFAULT_URL), properties);
    }
}
