package com.eventbooking.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidationTest {
    @Test
    void validatesEmailAndSeatCounts() {
        assertEquals("student@example.com", Validation.email("Student@Example.com"));
        assertEquals(1, Validation.seatCount(1));
        assertEquals(20, Validation.seatCount(20));
        assertThrows(IllegalArgumentException.class, () -> Validation.email("not-an-email"));
        assertThrows(IllegalArgumentException.class, () -> Validation.seatCount(0));
        assertThrows(IllegalArgumentException.class, () -> Validation.seatCount(21));
    }

    @Test
    void validatesFutureEventDatesAndCapacity() {
        String future = java.time.LocalDateTime.now().plusDays(30).withNano(0).toString();
        var event = Validation.event("Mini Project Expo", future, "Auditorium", 40);
        assertEquals(40, event.totalSeats());
        assertThrows(IllegalArgumentException.class,
                () -> Validation.event("Past event", "2020-01-01T10:00:00", "Hall", 10));
        assertThrows(IllegalArgumentException.class,
                () -> Validation.event("Invalid capacity", future, "Hall", 0));
    }
}
