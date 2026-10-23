package com.eventbooking.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Validation {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private Validation() {
    }

    public static String requiredText(String value, String label, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(label + " is required and must be at most " + maxLength + " characters.");
        }
        return value.trim();
    }

    public static String email(String value) {
        String email = requiredText(value, "Email", 254).toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        return email;
    }

    public static char[] password(char[] password) {
        if (password == null || password.length < 8 || password.length > 128) {
            throw new IllegalArgumentException("Password must contain between 8 and 128 characters.");
        }
        return password;
    }

    public static int seatCount(int seats) {
        if (seats < 1 || seats > 20) {
            throw new IllegalArgumentException("Seat count must be between 1 and 20.");
        }
        return seats;
    }

    public static long positiveId(long id, String label) {
        if (id < 1) {
            throw new IllegalArgumentException(label + " must be a positive number.");
        }
        return id;
    }

    public static EventInput event(String name, String date, String venue, int totalSeats) {
        String eventName = requiredText(name, "Event name", 160);
        String eventVenue = requiredText(venue, "Venue", 200);
        if (totalSeats < 1 || totalSeats > 100_000) {
            throw new IllegalArgumentException("Total seats must be between 1 and 100000.");
        }
        try {
            LocalDateTime parsed = LocalDateTime.parse(requiredText(date, "Event date", 30));
            if (parsed.isBefore(LocalDateTime.now())) {
                throw new IllegalArgumentException("Event date must be in the future.");
            }
            return new EventInput(eventName, parsed, eventVenue, totalSeats);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Event date must use ISO format yyyy-MM-ddTHH:mm:ss.", ex);
        }
    }

    public record EventInput(String name, LocalDateTime date, String venue, int totalSeats) {
    }
}
