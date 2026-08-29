package org.margin.server.authentication.models;

public record ResetPasswordRequest(String token, String newPassword) {}