package org.margin.server.integrationtest.config;

import org.margin.server.notifications.push.PushMessage;
import org.margin.server.notifications.push.PushPlatform;
import org.margin.server.notifications.push.PushSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test stand-in for the FCM/APNs senders (which are env-gated off in tests). Lives in the
 * shared [MarginTestRunner] context — a per-test-class `@Import` would fork a second
 * Spring context, which destabilizes the whole suite (context-cache eviction re-runs
 * expensive container/app startup mid-run).
 */
@TestConfiguration
public class CapturingPushSenderConfig {

    public record CapturedPush(String token, PushMessage message) {
    }

    public static final List<CapturedPush> captured = new CopyOnWriteArrayList<>();
    public static volatile boolean failNextSendAsInvalidToken = false;

    public static void reset() {
        captured.clear();
        failNextSendAsInvalidToken = false;
    }

    @Bean
    PushSender capturingPushSender() {
        return new PushSender() {
            @Override
            public PushPlatform platform() {
                return PushPlatform.ANDROID;
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public void send(String token, PushMessage message) {
                if (failNextSendAsInvalidToken) {
                    failNextSendAsInvalidToken = false;
                    throw new InvalidTokenException("test token invalidated");
                }
                captured.add(new CapturedPush(token, message));
            }
        };
    }
}
