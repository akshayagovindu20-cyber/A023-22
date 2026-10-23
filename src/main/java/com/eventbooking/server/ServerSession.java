package com.eventbooking.server;

import com.eventbooking.model.User;

import java.time.Instant;

public record ServerSession(User user, Instant lastActivity) {
    public ServerSession touch() {
        return new ServerSession(user, Instant.now());
    }
}
