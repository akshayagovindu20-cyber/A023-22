package com.eventbooking.model;

public record User(long userId, String fullName, String email, String passwordHash, String role) {
}
