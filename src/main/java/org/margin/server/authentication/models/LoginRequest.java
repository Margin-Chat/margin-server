package org.margin.server.authentication.models;

public record LoginRequest(String username, String password) {
}