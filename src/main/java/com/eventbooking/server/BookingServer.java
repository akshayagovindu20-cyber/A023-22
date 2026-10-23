package com.eventbooking.server;

import com.eventbooking.dao.BookingDao;
import com.eventbooking.dao.EventDao;
import com.eventbooking.dao.UserDao;
import com.eventbooking.service.AdminService;
import com.eventbooking.service.AuthService;
import com.eventbooking.service.BookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BookingServer implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(BookingServer.class.getName());
    private final int port;
    private final ThreadPoolExecutor workers;
    private final ConcurrentHashMap<String, ServerSession> sessions = new ConcurrentHashMap<>();
    private final Set<Socket> activeClients = ConcurrentHashMap.newKeySet();
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final AuthService authService = new AuthService(new UserDao());
    private final EventDao eventDao = new EventDao();
    private final BookingService bookingService = new BookingService(new BookingDao(), eventDao);
    private final AdminService adminService = new AdminService(eventDao);
    private volatile ServerSocket serverSocket;
    private volatile boolean running;

    public BookingServer(int port) {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Server port must be between 1 and 65535.");
        }
        this.port = port;
        int threads = Math.max(8, Runtime.getRuntime().availableProcessors() * 2);
        workers = new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(500), new ThreadPoolExecutor.AbortPolicy());
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        LOGGER.info(() -> "Event booking server listening on port " + port + ".");
        while (running) {
            try {
                Socket client = serverSocket.accept();
                client.setKeepAlive(true);
                activeClients.add(client);
                try {
                    workers.execute(new ClientHandler(client, mapper, authService,
                            bookingService, adminService, sessions, activeClients));
                } catch (java.util.concurrent.RejectedExecutionException ex) {
                    activeClients.remove(client);
                    client.close();
                    LOGGER.warning("Client rejected because the server is at capacity.");
                }
            } catch (SocketException ex) {
                if (running) {
                    throw ex;
                }
            }
        }
    }

    @Override
    public void close() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "Could not close listening socket cleanly.", ex);
            }
            for (Socket client : activeClients) {
                try {
                    client.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.FINE, "Could not close a client socket during shutdown.", ex);
                }
            }
        }
        workers.shutdown();
        try {
            if (!workers.awaitTermination(10, TimeUnit.SECONDS)) {
                workers.shutdownNow();
            }
        } catch (InterruptedException ex) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
