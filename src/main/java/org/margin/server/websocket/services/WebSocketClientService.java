package org.margin.server.websocket.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.models.SpaceChannelMessage;
import org.margin.server.social.models.SpaceChannel;
import org.margin.server.social.repositories.SpaceChannelMessageRepository;
import org.margin.server.social.repositories.SpaceChannelRepository;
import org.margin.server.social.repositories.SpaceRepository;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.springframework.stereotype.Service;
import org.margin.server.social.models.DirectMessage;
import org.margin.server.social.services.MessageService;
import org.margin.server.config.FederationConfig;
import org.margin.server.federation.services.FederationService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.factories.WebSocketMessageFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class WebSocketClientService {
    private final Map<Long, Channel> clients = new ConcurrentHashMap<>();
    private final WebSocketMessageFactory messageFactory;
    private final MessageService messageService;
    private final FederationConfig federationConfig;
    private final FederationService federationService;
    private final SpaceChannelRepository channelRepository;
    private final SpaceRepository spaceRepository;
    private final SpaceChannelMessageRepository spaceChannelMessageRepository;

    public WebSocketClientService(WebSocketMessageFactory messageFactory,
                                  MessageService messageService,
                                  FederationConfig federationConfig,
                                  FederationService federationService,
                                  SpaceChannelRepository channelRepository,
                                  SpaceRepository spaceRepository,
                                  SpaceChannelMessageRepository spaceChannelMessageRepository) {
        this.messageFactory = messageFactory;
        this.messageService = messageService;
        this.federationConfig = federationConfig;
        this.federationService = federationService;
        this.channelRepository = channelRepository;
        this.spaceRepository = spaceRepository;
        this.spaceChannelMessageRepository = spaceChannelMessageRepository;
    }

    public void addClient(Long id, Channel channel) {
        clients.put(id, channel);
        log.info("User {} has connected", id);
    }

    public void removeClient(Long id) {
        clients.remove(id);
        log.info("User {} has disconnected", id);
    }

    public void logoutUser(Long userId) {
        Channel channel = clients.get(userId);
        if (channel.isActive() || channel.isOpen()) {
            channel.close();
        }
    }

    public Map<Long, Channel> getAllClients() {
        return clients;
    }

    public Channel getClientChannel(Long toUserId) {
        return clients.get(toUserId);
    }

    public void broadcastUserLogin(User loggedInUser) {
        try {
            String messageJson = messageFactory.createUserActivity(WebSocketMessageType.USER_LOGIN, loggedInUser);
            TextWebSocketFrame frame = new TextWebSocketFrame(messageJson);

            Channel loggedInUserChannel = clients.get(loggedInUser.getId());

            for (Channel channel : clients.values()) {
                if (channel.isActive() && channel != loggedInUserChannel) {
                    channel.writeAndFlush(frame.copy());
                }
            }

            frame.release();
        } catch (Exception e) {
            log.error("Error broadcasting user login: {}", e.getMessage());
        }
    }

    public void broadcastUserLogout(User loggedOutUser) {
        try {
            String messageJson = messageFactory.createUserActivity(WebSocketMessageType.USER_LOGOUT, loggedOutUser);
            TextWebSocketFrame frame = new TextWebSocketFrame(messageJson);

            Channel loggedOutUserChannel = clients.get(loggedOutUser.getId());

            for (Channel channel : clients.values()) {
                if (channel.isActive() && channel != loggedOutUserChannel) {
                    channel.writeAndFlush(frame.copy());
                }
            }

            frame.release();
        } catch (Exception e) {
            log.error("Error broadcasting user logout: {}", e.getMessage());
        }
    }

    public void sendMessageToUser(DirectMessage directMessage) {
        String recipientServer = parseServerFromUserId(directMessage.getToUserServer());

        if (isLocalUser(recipientServer)) {
            deliverLocalMessage(directMessage);
        } else {
            deliverFederatedMessage(directMessage, recipientServer);
        }
    }

    public void sendMessageToChannel(User user, Long toChannelId, String messageText) {
        Optional<SpaceChannel> channel = channelRepository.findById(toChannelId);

        if (channel.isEmpty()) {
            return;
        }

        SpaceChannelMessage channelMessage = new SpaceChannelMessage(
                user.getId(),
                toChannelId,
                channel.get().getSpaceId(),
                messageText
        );
        spaceChannelMessageRepository.save(channelMessage);

        List<User> usersForSpace = spaceRepository.getUsersForSpace(channel.get().getSpaceId());

        for (User recipientUser : usersForSpace) {
            if (recipientUser.getId().equals(user.getId())) {
                continue;
            }

            log.info("Looking for user {} in clients map. Clients: {}", recipientUser.getId(), clients);
            Channel targetChannel = getClientChannel(recipientUser.getId());
            log.info("Channel found: {}", targetChannel);

            try {
                String messageJson = messageFactory.createWebSocketChannelMessage(channelMessage);
                TextWebSocketFrame frame = new TextWebSocketFrame(messageJson);

                if (targetChannel.isActive()) {
                    targetChannel.writeAndFlush(frame.copy());
                } else {
                    log.debug("Channel inactive for user {}", recipientUser.getId());
                    // TODO: Send push notification for offline user
                }

                frame.release();
            } catch (JsonProcessingException e) {
                log.error("Error sending message: {}", e.getMessage());
            }
        }
    }

    public void sendCallOffer(User fromUser, JsonNode receivedMessage) {
        String offerMessage = receivedMessage.get("message").toString();
        String toUserIdIdentifier = receivedMessage.get("toUserId").asText();
        Channel targetChannel = getClientChannel(Long.valueOf(toUserIdIdentifier));

        try {
            String messageJson = messageFactory.createWebSocketCallMessage(
                    fromUser,
                    offerMessage,
                    WebSocketMessageType.CALL_OFFER);
            TextWebSocketFrame frame = new TextWebSocketFrame(messageJson);
            if (targetChannel.isActive()) {
                targetChannel.writeAndFlush(frame.copy());
            }
        } catch (JsonProcessingException e) {
            log.error("Error sending message: {}", e.getMessage());
            // TODO: Call back error to calling user
        }
    }

    public void sendCallResponse(User fromUser, JsonNode receivedMessage) {
        String offerMessage = receivedMessage.get("message").toString();
        String toUserIdIdentifier = receivedMessage.get("toUserId").asText();
        Channel targetChannel = getClientChannel(Long.valueOf(toUserIdIdentifier));

        try {
            String messageJson = messageFactory.createWebSocketCallMessage(
                    fromUser,
                    offerMessage,
                    WebSocketMessageType.CALL_RESPONSE);
            TextWebSocketFrame frame = new TextWebSocketFrame(messageJson);
            if (targetChannel.isActive()) {
                targetChannel.writeAndFlush(frame.copy());
            }
        } catch (JsonProcessingException e) {
            log.error("Error sending message: {}", e.getMessage());
            // TODO: Call back error to calling user
        }
    }

    public void sendCallCandidate(User fromUser, JsonNode receivedMessage) {
        String offerMessage = receivedMessage.get("message").toString();
        String toUserIdIdentifier = receivedMessage.get("toUserId").asText();
        Channel targetChannel = getClientChannel(Long.valueOf(toUserIdIdentifier));

        try {
            String messageJson = messageFactory.createWebSocketCallMessage(
                    fromUser,
                    offerMessage,
                    WebSocketMessageType.CALL_CANDIDATE);
            TextWebSocketFrame frame = new TextWebSocketFrame(messageJson);
            if (targetChannel.isActive()) {
                targetChannel.writeAndFlush(frame.copy());
            }
        } catch (JsonProcessingException e) {
            log.error("Error sending message: {}", e.getMessage());
            // TODO: Call back error to calling user
        }
    }

    public void sendCallEnd(User fromUser, JsonNode receivedMessage) {
        String offerMessage = receivedMessage.get("message").toString();
        String toUserIdIdentifier = receivedMessage.get("toUserId").asText();
        Channel targetChannel = getClientChannel(Long.valueOf(toUserIdIdentifier));

        try {
            String messageJson = messageFactory.createWebSocketCallMessage(
                    fromUser,
                    offerMessage,
                    WebSocketMessageType.CALL_END);
            TextWebSocketFrame frame = new TextWebSocketFrame(messageJson);
            if (targetChannel.isActive()) {
                targetChannel.writeAndFlush(frame.copy());
            }
        } catch (JsonProcessingException e) {
            log.error("Error sending message: {}", e.getMessage());
            // TODO: Call back error to calling user
        }
    }

    public void clearAllClients() {
        clients.clear();
    }

    private void deliverLocalMessage(DirectMessage directMessage) {
        Channel targetChannel = getClientChannel(directMessage.getToUserId());
        messageService.saveDirectMessage(directMessage);

        if (targetChannel == null) {
            log.debug("User {} is not connected, storing message for later", directMessage.getToUserId());
            // TODO: Send push notification for offline user
            return;
        }

        try {
            String messageJson = messageFactory.createWebSocketChatMessage(directMessage);
            TextWebSocketFrame frame = new TextWebSocketFrame(messageJson);

            if (targetChannel.isActive()) {
                targetChannel.writeAndFlush(frame.copy());
            } else {
                log.debug("Channel inactive for user {}", directMessage.getToUserId());
                // TODO: Send push notification for offline user
            }

            frame.release();
        } catch (JsonProcessingException e) {
            log.error("Error sending message: {}", e.getMessage());
        }
    }

    private void deliverFederatedMessage(DirectMessage directMessage, String targetServer) {
        log.info("Routing message to federated server: {}", targetServer);

        if (directMessage.getFromUserServer() == null) {
            directMessage.setFromUserServer(federationConfig.getServerDomain());
        }
        directMessage.setToUserServer(targetServer);

        messageService.saveDirectMessage(directMessage);

        boolean success = federationService.sendFederatedMessage(directMessage);
        if (!success) {
            log.error("Failed to deliver federated message to {}", targetServer);
            // TODO: Mark message as failed, implement retry logic or callback the error to client
        }
    }

    private boolean isLocalUser(String serverDomain) {
        return serverDomain == null ||
                serverDomain.isEmpty() ||
                serverDomain.equals(federationConfig.getServerDomain());
    }

    private String parseServerFromUserId(String serverHint) {
        if (serverHint != null && !serverHint.isEmpty()) {
            return serverHint;
        }

        return federationConfig.getServerDomain();
    }
}
