package org.margin.server.notifications.push;

import org.margin.server.websocket.connection.ConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Executor;

@Service
public class PushDispatchService {

    private static final Logger log = LoggerFactory.getLogger(PushDispatchService.class);

    private final PushTokenRepository pushTokenRepository;
    private final ConnectionManager connectionManager;
    private final List<PushSender> senders;
    private final Executor pushExecutor;

    public PushDispatchService(PushTokenRepository pushTokenRepository,
                               ConnectionManager connectionManager,
                               List<PushSender> senders,
                               @Qualifier("pushExecutor") Executor pushExecutor) {
        this.pushTokenRepository = pushTokenRepository;
        this.connectionManager = connectionManager;
        this.senders = senders;
        this.pushExecutor = pushExecutor;
    }

    public void pushToUserIfOffline(Long userId, PushMessage message) {
        if (connectionManager.isUserOnline(userId)) {
            return;
        }
        List<PushToken> tokens = pushTokenRepository.findByUserId(userId);
        if (tokens.isEmpty()) {
            return;
        }
        pushExecutor.execute(() -> deliver(userId, tokens, message));
    }

    private void deliver(Long userId, List<PushToken> tokens, PushMessage message) {
        for (PushToken token : tokens) {
            senders.stream()
                    .filter(sender -> sender.platform() == token.getPlatform() && sender.isEnabled())
                    .forEach(sender -> {
                        try {
                            sender.send(token.getToken(), message);
                        } catch (PushSender.InvalidTokenException e) {
                            log.info("Removing dead {} push token for user {}", token.getPlatform(), userId);
                            pushTokenRepository.delete(token);
                        } catch (Exception e) {
                            log.warn("Push delivery failed for user {}: {}", userId, e.getMessage());
                        }
                    });
        }
    }
}
