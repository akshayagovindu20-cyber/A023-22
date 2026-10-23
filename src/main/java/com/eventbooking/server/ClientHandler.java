package com.eventbooking.server;

import com.eventbooking.model.User;
import com.eventbooking.network.Request;
import com.eventbooking.network.Response;
import com.eventbooking.service.AdminService;
import com.eventbooking.service.AuthService;
import com.eventbooking.service.BookingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientHandler implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());
    private static final int MAX_REQUEST_CHARS = 1_000_000;
    private static final long SESSION_TTL_SECONDS = 8 * 60 * 60;
    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    private final Socket socket;
    private final ObjectMapper mapper;
    private final AuthService authService;
    private final BookingService bookingService;
    private final AdminService adminService;
    private final ConcurrentMap<String, ServerSession> sessions;
    private final Set<Socket> activeClients;

    public ClientHandler(Socket socket, ObjectMapper mapper, AuthService authService,
                         BookingService bookingService, AdminService adminService,
                         ConcurrentMap<String, ServerSession> sessions, Set<Socket> activeClients) {
        this.socket = socket;
        this.mapper = mapper;
        this.authService = authService;
        this.bookingService = bookingService;
        this.adminService = adminService;
        this.sessions = sessions;
        this.activeClients = activeClients;
    }

    @Override
    public void run() {
        try (socket;
             BufferedReader input = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter output = new BufferedWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = input.readLine()) != null) {
                Response response;
                if (line.length() > MAX_REQUEST_CHARS) {
                    response = Response.error("Request is too large.");
                } else {
                    response = process(line);
                }
                output.write(mapper.writeValueAsString(response));
                output.newLine();
                output.flush();
            }
        } catch (IOException ex) {
            if (!socket.isClosed()) {
                LOGGER.log(Level.FINE, "Client connection ended: " + ex.getMessage());
            }
        } finally {
            activeClients.remove(socket);
        }
    }

    private Response process(String line) {
        try {
            Request request = mapper.readValue(line, Request.class);
            if (request.action() == null || request.action().isBlank()) {
                return Response.error("Request action is required.");
            }
            JsonNode payload = request.payload() == null ? mapper.createObjectNode() : request.payload();
            if (!payload.isObject()) {
                return Response.error("Request payload must be a JSON object.");
            }
            return dispatch(request.action(), request.token(), payload);
        } catch (JsonProcessingException ex) {
            return Response.error("Malformed request. Send one valid JSON request per line.");
        } catch (IllegalArgumentException | SecurityException ex) {
            return Response.error(ex.getMessage());
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Database request failed.", ex);
            return Response.error("Database operation failed. Check that MySQL is available and configured.");
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Unexpected client request failure.", ex);
            return Response.error("The server could not complete the request.");
        }
    }

    private Response dispatch(String action, String token, JsonNode payload) throws SQLException {
        return switch (action) {
            case "REGISTER" -> {
                User user = authService.register(text(payload, "fullName"), text(payload, "email"),
                        text(payload, "password").toCharArray());
                yield Response.success("Registration successful. You can now log in.",
                        Map.of("fullName", user.fullName(), "email", user.email()));
            }
            case "LOGIN" -> {
                User user = authService.login(text(payload, "email"), text(payload, "password").toCharArray());
                String newToken = createSession(user);
                yield Response.success("Login successful.", Map.of(
                        "token", newToken, "fullName", user.fullName(),
                        "email", user.email(), "role", user.role()));
            }
            case "EVENTS" -> Response.success("Events loaded.", bookingService.listEvents());
            case "LOGOUT" -> {
                if (token != null) {
                    sessions.remove(token);
                }
                yield Response.success("Logged out.", Map.of());
            }
            case "BOOK" -> {
                User user = authenticated(token);
                var booking = bookingService.book(user.userId(), positiveLong(payload, "eventId"),
                        integer(payload, "seats"));
                yield Response.success("Booking confirmed.", booking);
            }
            case "MY_BOOKINGS" -> {
                User user = authenticated(token);
                yield Response.success("Booking history loaded.", bookingService.bookingsForUser(user.userId()));
            }
            case "CANCEL" -> {
                User user = authenticated(token);
                bookingService.cancel(text(payload, "bookingId"), user.userId(), "ADMIN".equals(user.role()));
                yield Response.success("Booking cancelled and seats restored.", Map.of());
            }
            case "ADMIN_CREATE_EVENT" -> {
                requireAdministrator(authenticated(token));
                var event = adminService.createEvent(text(payload, "eventName"), text(payload, "eventDate"),
                        text(payload, "venue"), integer(payload, "totalSeats"));
                yield Response.success("Event created.", event);
            }
            case "ADMIN_UPDATE_EVENT" -> {
                requireAdministrator(authenticated(token));
                var event = adminService.updateEvent(positiveLong(payload, "eventId"),
                        text(payload, "eventName"), text(payload, "eventDate"),
                        text(payload, "venue"), integer(payload, "totalSeats"));
                yield Response.success("Event updated.", event);
            }
            case "ADMIN_DELETE_EVENT" -> {
                requireAdministrator(authenticated(token));
                adminService.deleteEvent(positiveLong(payload, "eventId"));
                yield Response.success("Event deleted.", Map.of());
            }
            case "ADMIN_BOOKINGS" -> {
                requireAdministrator(authenticated(token));
                yield Response.success("All bookings loaded.", bookingService.allBookings());
            }
            default -> Response.error("Unknown request action.");
        };
    }

    private String createSession(User user) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new ServerSession(user, Instant.now()));
        return token;
    }

    private User authenticated(String token) {
        if (token == null || token.isBlank()) {
            throw new SecurityException("Please log in to perform this operation.");
        }
        ServerSession session = sessions.get(token);
        if (session == null) {
            throw new SecurityException("Session is invalid or expired. Please log in again.");
        }
        if (session.lastActivity().plusSeconds(SESSION_TTL_SECONDS).isBefore(Instant.now())) {
            sessions.remove(token, session);
            throw new SecurityException("Session expired. Please log in again.");
        }
        sessions.put(token, session.touch());
        return session.user();
    }

    private void requireAdministrator(User user) {
        if (!"ADMIN".equals(user.role())) {
            throw new SecurityException("Administrator access is required.");
        }
    }

    private static String text(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException("Field '" + field + "' is required.");
        }
        return value.textValue();
    }

    private static int integer(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || !value.canConvertToInt()) {
            throw new IllegalArgumentException("Field '" + field + "' must be an integer.");
        }
        return value.intValue();
    }

    private static long positiveLong(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || !value.canConvertToLong() || value.longValue() < 1) {
            throw new IllegalArgumentException("Field '" + field + "' must be a positive integer.");
        }
        return value.longValue();
    }
}
