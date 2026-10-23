package com.eventbooking.client;

import com.eventbooking.model.Booking;
import com.eventbooking.model.Event;
import com.eventbooking.network.Response;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class BookingClient implements AutoCloseable {
    private final String host;
    private final int port;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private Socket socket;
    private BufferedReader input;
    private BufferedWriter output;
    private String token;

    public BookingClient() {
        host = System.getenv().getOrDefault("SERVER_HOST", "127.0.0.1");
        port = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "5050"));
    }

    public synchronized LoginInfo login(String email, char[] password) throws IOException {
        JsonNode data = send("LOGIN", Map.of("email", email, "password", new String(password)));
        token = data.path("token").asText();
        if (token.isBlank()) {
            throw new IOException("The server did not return a login session.");
        }
        return new LoginInfo(data.path("fullName").asText(), data.path("email").asText(),
                data.path("role").asText());
    }

    public synchronized void register(String fullName, String email, char[] password) throws IOException {
        send("REGISTER", Map.of("fullName", fullName, "email", email, "password", new String(password)));
    }

    public synchronized List<Event> events() throws IOException {
        return convertList(send("EVENTS", Map.of()), Event.class);
    }

    public synchronized Booking book(long eventId, int seats) throws IOException {
        return mapper.convertValue(send("BOOK", Map.of("eventId", eventId, "seats", seats)), Booking.class);
    }

    public synchronized List<Booking> myBookings() throws IOException {
        return convertList(send("MY_BOOKINGS", Map.of()), Booking.class);
    }

    public synchronized List<Booking> allBookings() throws IOException {
        return convertList(send("ADMIN_BOOKINGS", Map.of()), Booking.class);
    }

    public synchronized void cancel(String bookingId) throws IOException {
        send("CANCEL", Map.of("bookingId", bookingId));
    }

    public synchronized Event createEvent(String name, String date, String venue, int seats) throws IOException {
        return mapper.convertValue(send("ADMIN_CREATE_EVENT", Map.of(
                "eventName", name, "eventDate", date, "venue", venue, "totalSeats", seats)), Event.class);
    }

    public synchronized Event updateEvent(long id, String name, String date, String venue, int seats)
            throws IOException {
        return mapper.convertValue(send("ADMIN_UPDATE_EVENT", Map.of(
                "eventId", id, "eventName", name, "eventDate", date,
                "venue", venue, "totalSeats", seats)), Event.class);
    }

    public synchronized void deleteEvent(long eventId) throws IOException {
        send("ADMIN_DELETE_EVENT", Map.of("eventId", eventId));
    }

    public synchronized void logout() throws IOException {
        if (token != null) {
            try {
                send("LOGOUT", Map.of());
            } finally {
                token = null;
                closeConnection();
            }
        }
    }

    private <T> List<T> convertList(JsonNode data, Class<T> type) {
        return mapper.convertValue(data, mapper.getTypeFactory().constructCollectionType(List.class, type));
    }

    private JsonNode send(String action, Object payload) throws IOException {
        ensureConnected();
        try {
            String request = mapper.writeValueAsString(Map.of(
                    "action", action,
                    "token", token == null ? "" : token,
                    "payload", payload));
            output.write(request);
            output.newLine();
            output.flush();
            String responseLine = input.readLine();
            if (responseLine == null) {
                throw new IOException("The server closed the connection.");
            }
            Response response = mapper.readValue(responseLine, Response.class);
            if (!response.success()) {
                throw new ServerException(response.message());
            }
            return mapper.valueToTree(response.data());
        } catch (IOException ex) {
            closeConnection();
            throw ex;
        }
    }

    private void ensureConnected() throws IOException {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            return;
        }
        closeConnection();
        socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), 5_000);
            socket.setSoTimeout(30_000);
            socket.setKeepAlive(true);
            input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            closeConnection();
            throw new IOException("Could not connect to the event booking server at " + host + ":" + port
                    + ". Start the server and check the host and port.", ex);
        }
    }

    private void closeConnection() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // Closing a failed client connection is best-effort; the original request error is reported.
        } finally {
            socket = null;
            input = null;
            output = null;
        }
    }

    @Override
    public synchronized void close() {
        token = null;
        closeConnection();
    }

    public record LoginInfo(String fullName, String email, String role) {
    }

    private static final class ServerException extends IOException {
        private ServerException(String message) {
            super(message);
        }
    }
}
