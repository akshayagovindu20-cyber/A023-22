package com.eventbooking.service;

import com.eventbooking.dao.EventDao;
import com.eventbooking.model.Event;
import com.eventbooking.util.Validation;

import java.sql.SQLException;

public class AdminService {
    private final EventDao events;

    public AdminService(EventDao events) {
        this.events = events;
    }

    public Event createEvent(String name, String date, String venue, int seats) throws SQLException {
        return events.create(Validation.event(name, date, venue, seats));
    }

    public Event updateEvent(long eventId, String name, String date, String venue, int seats) throws SQLException {
        return events.update(Validation.positiveId(eventId, "Event ID"),
                Validation.event(name, date, venue, seats));
    }

    public void deleteEvent(long eventId) throws SQLException {
        events.delete(Validation.positiveId(eventId, "Event ID"));
    }
}
