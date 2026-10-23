package com.eventbooking.network;

import com.fasterxml.jackson.databind.JsonNode;

public record Request(String action, String token, JsonNode payload) {
}
