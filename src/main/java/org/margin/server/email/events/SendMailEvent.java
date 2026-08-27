package org.margin.server.email.events;

public record SendMailEvent(String to, String subject, String htmlBody) {
}
