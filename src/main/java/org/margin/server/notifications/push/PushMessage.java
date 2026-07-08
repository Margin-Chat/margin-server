package org.margin.server.notifications.push;

import java.util.Map;

/**
 * Platform-neutral push payload. [data] rides along for client-side routing (e.g.
 * conversationId to open on tap); [title]/[body] are display text. For encrypted
 * conversations the body must stay generic — the server only holds ciphertext.
 */
public record PushMessage(String title, String body, Map<String, String> data) {
}
