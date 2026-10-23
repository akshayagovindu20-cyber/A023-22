package com.eventbooking.service;

import com.eventbooking.dao.BookingDao;
import com.eventbooking.dao.EventDao;
import com.eventbooking.model.Booking;
import com.eventbooking.model.Event;
import com.eventbooking.util.Validation;

import java.sql.SQLException;
import java.util.List;

public class BookingService {
    private final BookingDao bookings;
    private final EventDao events;

    public BookingService(BookingDao bookings, EventDao events) {
        this.bookings = bookings;
        this.events = events;
    }

    public List<Event> listEvents() throws SQLException {
        return events.findAll();
    }

    public Booking book(long userId, long eventId, int seats) throws SQLException {
        Validation.positiveId(userId, "User ID");
        Validation.positiveId(eventId, "Event ID");
        Validation.seatCount(seats);
        return bookings.create(userId, eventId, seats);
    }

    public List<Booking> bookingsForUser(long userId) throws SQLException {
        return bookings.findForUser(Validation.positiveId(userId, "User ID"));
    }

    public List<Booking> allBookings() throws SQLException {
        return bookings.findAll();
    }

    public void cancel(String bookingId, long requesterId, boolean administrator) throws SQLException {
        if (bookingId == null || !bookingId.matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new IllegalArgumentException("Booking ID is invalid.");
        }
        bookings.cancel(bookingId, Validation.positiveId(requesterId, "User ID"), administrator);
    }
}
