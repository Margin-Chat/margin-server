package org.margin.server.authentication.models;

public record LoginRequest(String email, String password) {
}