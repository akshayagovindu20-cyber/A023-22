package com.eventbooking.server;

import com.eventbooking.dao.UserDao;
import com.eventbooking.service.AuthService;
import com.eventbooking.util.Database;

import java.sql.Connection;

public final class ServerMain {
    private ServerMain() {
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "5050"));
        try (Connection ignored = Database.getConnection()) {
            System.out.println("Database connection successful.");
        }
        new AuthService(new UserDao()).bootstrapAdminFromEnvironment();
        BookingServer server = new BookingServer(port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "server-shutdown"));
        try (server) {
            server.start();
        }
    }
}
