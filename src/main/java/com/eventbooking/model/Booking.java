package com.eventbooking.model;

import java.time.LocalDateTime;

public record Booking(
        String bookingId,
        long userId,
        String customerName,
        long eventId,
        String eventName,
        int seatsBooked,
        String bookingStatus,
        LocalDateTime bookingTime) {
}
