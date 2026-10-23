package com.eventbooking.network;

public record Response(boolean success, String message, Object data) {
    public static Response success(String message, Object data) {
        return new Response(true, message, data);
    }

    public static Response error(String message) {
        return new Response(false, message, null);
    }
}
