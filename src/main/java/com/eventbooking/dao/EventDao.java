package com.eventbooking.dao;

import com.eventbooking.model.Event;
import com.eventbooking.util.Database;
import com.eventbooking.util.Validation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class EventDao {
    public List<Event> findAll() throws SQLException {
        String sql = "SELECT event_id, event_name, event_date, venue, total_seats, available_seats "
                + "FROM events ORDER BY event_date, event_id";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            List<Event> events = new ArrayList<>();
            while (result.next()) {
                events.add(map(result));
            }
            return events;
        }
    }

    public Event create(Validation.EventInput input) throws SQLException {
        String sql = "INSERT INTO events (event_name, event_date, venue, total_seats, available_seats) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, input.name());
            statement.setTimestamp(2, Timestamp.valueOf(input.date()));
            statement.setString(3, input.venue());
            statement.setInt(4, input.totalSeats());
            statement.setInt(5, input.totalSeats());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("The database did not return the new event's ID.");
                }
                return new Event(keys.getLong(1), input.name(), input.date(), input.venue(),
                        input.totalSeats(), input.totalSeats());
            }
        }
    }

    public Event update(long eventId, Validation.EventInput input) throws SQLException {
        String lockSql = "SELECT event_id FROM events WHERE event_id = ? FOR UPDATE";
        String bookedSql = "SELECT COALESCE(SUM(seats_booked), 0) FROM bookings "
                + "WHERE event_id = ? AND booking_status = 'CONFIRMED'";
        String updateSql = "UPDATE events SET event_name = ?, event_date = ?, venue = ?, "
                + "total_seats = ?, available_seats = ? WHERE event_id = ?";
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement lock = connection.prepareStatement(lockSql)) {
                    lock.setLong(1, eventId);
                    try (ResultSet result = lock.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalArgumentException("Event not found.");
                        }
                    }
                }
                int bookedSeats;
                try (PreparedStatement booked = connection.prepareStatement(bookedSql)) {
                    booked.setLong(1, eventId);
                    try (ResultSet result = booked.executeQuery()) {
                        result.next();
                        bookedSeats = result.getInt(1);
                    }
                }
                if (input.totalSeats() < bookedSeats) {
                    throw new IllegalArgumentException("Total seats cannot be lower than the number already booked ("
                            + bookedSeats + ").");
                }
                try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                    update.setString(1, input.name());
                    update.setTimestamp(2, Timestamp.valueOf(input.date()));
                    update.setString(3, input.venue());
                    update.setInt(4, input.totalSeats());
                    update.setInt(5, input.totalSeats() - bookedSeats);
                    update.setLong(6, eventId);
                    update.executeUpdate();
                }
                connection.commit();
                return new Event(eventId, input.name(), input.date(), input.venue(),
                        input.totalSeats(), input.totalSeats() - bookedSeats);
            } catch (SQLException | RuntimeException ex) {
                rollback(connection, ex);
                throw ex;
            }
        }
    }

    public void delete(long eventId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement lock = connection.prepareStatement(
                        "SELECT event_id FROM events WHERE event_id = ? FOR UPDATE")) {
                    lock.setLong(1, eventId);
                    try (ResultSet result = lock.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalArgumentException("Event not found.");
                        }
                    }
                }
                try (PreparedStatement count = connection.prepareStatement(
                        "SELECT COUNT(*) FROM bookings WHERE event_id = ?")) {
                    count.setLong(1, eventId);
                    try (ResultSet result = count.executeQuery()) {
                        result.next();
                        if (result.getLong(1) > 0) {
                            throw new IllegalArgumentException(
                                    "This event has booking history and cannot be deleted. Keep it for audit history.");
                        }
                    }
                }
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM events WHERE event_id = ?")) {
                    delete.setLong(1, eventId);
                    delete.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(connection, ex);
                throw ex;
            }
        }
    }

    private Event map(ResultSet result) throws SQLException {
        Timestamp date = result.getTimestamp("event_date");
        return new Event(result.getLong("event_id"), result.getString("event_name"),
                date.toLocalDateTime(), result.getString("venue"),
                result.getInt("total_seats"), result.getInt("available_seats"));
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }
}
