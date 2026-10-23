package com.eventbooking.dao;

import com.eventbooking.model.Booking;
import com.eventbooking.util.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BookingDao {
    public Booking create(long userId, long eventId, int seats) throws SQLException {
        String bookingId = UUID.randomUUID().toString();
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement lock = connection.prepareStatement(
                        "SELECT event_id, event_name, event_date FROM events WHERE event_id = ? FOR UPDATE")) {
                    lock.setLong(1, eventId);
                    try (ResultSet result = lock.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalArgumentException("Event not found.");
                        }
                        if (result.getTimestamp("event_date").toLocalDateTime().isBefore(LocalDateTime.now())) {
                            throw new IllegalArgumentException("This event has already taken place.");
                        }
                    }
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE events SET available_seats = available_seats - ? "
                                + "WHERE event_id = ? AND available_seats >= ?")) {
                    update.setInt(1, seats);
                    update.setLong(2, eventId);
                    update.setInt(3, seats);
                    if (update.executeUpdate() != 1) {
                        throw new IllegalArgumentException("Not enough seats are available for this booking.");
                    }
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO bookings (booking_id, user_id, event_id, seats_booked, booking_status) "
                                + "VALUES (?, ?, ?, ?, 'CONFIRMED')")) {
                    insert.setString(1, bookingId);
                    insert.setLong(2, userId);
                    insert.setLong(3, eventId);
                    insert.setInt(4, seats);
                    insert.executeUpdate();
                }
                Booking booking = findById(connection, bookingId);
                connection.commit();
                return booking;
            } catch (SQLException | RuntimeException ex) {
                rollback(connection, ex);
                throw ex;
            }
        }
    }

    public List<Booking> findForUser(long userId) throws SQLException {
        return query("WHERE b.user_id = ?", userId);
    }

    public List<Booking> findAll() throws SQLException {
        return query("", null);
    }

    public void cancel(String bookingId, long requesterId, boolean administrator) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long ownerId;
                long eventId;
                int seats;
                String status;
                try (PreparedStatement lock = connection.prepareStatement(
                        "SELECT user_id, event_id, seats_booked, booking_status "
                                + "FROM bookings WHERE booking_id = ? FOR UPDATE")) {
                    lock.setString(1, bookingId);
                    try (ResultSet result = lock.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalArgumentException("Booking not found.");
                        }
                        ownerId = result.getLong("user_id");
                        eventId = result.getLong("event_id");
                        seats = result.getInt("seats_booked");
                        status = result.getString("booking_status");
                    }
                }
                if (!administrator && ownerId != requesterId) {
                    throw new SecurityException("You may cancel only your own bookings.");
                }
                if (!"CONFIRMED".equals(status)) {
                    throw new IllegalArgumentException("This booking has already been cancelled.");
                }
                try (PreparedStatement eventLock = connection.prepareStatement(
                        "SELECT event_id FROM events WHERE event_id = ? FOR UPDATE")) {
                    eventLock.setLong(1, eventId);
                    try (ResultSet result = eventLock.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException("The booking's event no longer exists.");
                        }
                    }
                }
                try (PreparedStatement restore = connection.prepareStatement(
                        "UPDATE events SET available_seats = available_seats + ? "
                                + "WHERE event_id = ? AND available_seats + ? <= total_seats")) {
                    restore.setInt(1, seats);
                    restore.setLong(2, eventId);
                    restore.setInt(3, seats);
                    if (restore.executeUpdate() != 1) {
                        throw new SQLException("Seat counts are inconsistent; the cancellation was not applied.");
                    }
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE bookings SET booking_status = 'CANCELLED' "
                                + "WHERE booking_id = ? AND booking_status = 'CONFIRMED'")) {
                    update.setString(1, bookingId);
                    if (update.executeUpdate() != 1) {
                        throw new SQLException("Booking status changed unexpectedly; cancellation was not applied.");
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(connection, ex);
                throw ex;
            }
        }
    }

    private Booking findById(Connection connection, String bookingId) throws SQLException {
        String sql = "SELECT b.booking_id, b.user_id, u.full_name, b.event_id, e.event_name, "
                + "b.seats_booked, b.booking_status, b.booking_time "
                + "FROM bookings b JOIN users u ON u.user_id = b.user_id "
                + "JOIN events e ON e.event_id = b.event_id WHERE b.booking_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, bookingId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("The committed booking could not be read back.");
                }
                return map(result);
            }
        }
    }

    private List<Booking> query(String filter, Long userId) throws SQLException {
        String sql = "SELECT b.booking_id, b.user_id, u.full_name, b.event_id, e.event_name, "
                + "b.seats_booked, b.booking_status, b.booking_time "
                + "FROM bookings b JOIN users u ON u.user_id = b.user_id "
                + "JOIN events e ON e.event_id = b.event_id " + filter
                + " ORDER BY b.booking_time DESC";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (userId != null) {
                statement.setLong(1, userId);
            }
            try (ResultSet result = statement.executeQuery()) {
                List<Booking> bookings = new ArrayList<>();
                while (result.next()) {
                    bookings.add(map(result));
                }
                return bookings;
            }
        }
    }

    private Booking map(ResultSet result) throws SQLException {
        Timestamp time = result.getTimestamp("booking_time");
        return new Booking(result.getString("booking_id"), result.getLong("user_id"),
                result.getString("full_name"), result.getLong("event_id"),
                result.getString("event_name"), result.getInt("seats_booked"),
                result.getString("booking_status"), time.toLocalDateTime());
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }
}
