package org.margin.server.notifications.push;

import java.util.Map;

public record PushMessage(String title, String body, Map<String, String> data) {
}
