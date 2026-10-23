package com.eventbooking.model;

import java.time.LocalDateTime;

public record Event(
        long eventId,
        String eventName,
        LocalDateTime eventDate,
        String venue,
        int totalSeats,
        int availableSeats) {
}
