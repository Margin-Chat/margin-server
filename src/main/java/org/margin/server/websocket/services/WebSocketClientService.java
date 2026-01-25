package org.margin.server.websocket.services;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.communication.calls.models.CallStatus;
import org.margin.server.social.communication.calls.models.CallType;
import org.margin.server.social.communication.calls.services.CallService;
import org.margin.server.social.communication.messages.models.SpaceChannelMessage;
import org.margin.server.social.models.SpaceChannel;
import org.margin.server.social.communication.messages.repositories.SpaceChannelMessageRepository;
import org.margin.server.social.repositories.SpaceChannelRepository;
import org.margin.server.social.repositories.SpaceRepository;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.*;
import org.springframework.stereotype.Service;
import org.margin.server.social.communication.messages.models.DirectMessage;
import org.margin.server.social.communication.messages.services.MessageService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.utils.WebSocketMessageBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class WebSocketClientService {
    private final Map<Long, Channel> clients = new ConcurrentHashMap<>();
    private final WebSocketMessageBuilder messageFactory;
    private final MessageService messageService;
    private final SpaceChannelRepository channelRepository;
    private final SpaceRepository spaceRepository;
    private final SpaceChannelMessageRepository spaceChannelMessageRepository;
    private final CallService callService;

    public WebSocketClientService(WebSocketMessageBuilder messageFactory,
                                  MessageService messageService,
                                  SpaceChannelRepository channelRepository,
                                  SpaceRepository spaceRepository,
                                  SpaceChannelMessageRepository spaceChannelMessageRepository,
                                  CallService callService) {
        this.messageFactory = messageFactory;
        this.messageService = messageService;
        this.channelRepository = channelRepository;
        this.spaceRepository = spaceRepository;
        this.spaceChannelMessageRepository = spaceChannelMessageRepository;
        this.callService = callService;
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
        deliverMessageToUser(directMessage);
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

            String messageJson = messageFactory.createWebSocketChannelMessage(channelMessage);
            TextWebSocketFrame frame = new TextWebSocketFrame(messageJson);

            if (targetChannel.isActive()) {
                targetChannel.writeAndFlush(frame.copy());
            } else {
                log.debug("Channel inactive for user {}", recipientUser.getId());
                // TODO: Send push notification for offline user
            }

            frame.release();
        }
    }

    public void sendCallOffer(User fromUser, WebSocketMessageIn<IncomingCallOfferPayload> request) {
        Long callId = callService.createNewCall(
                fromUser.getId(),
                request.getRecipientId(),
                CallStatus.OFFERED,
                CallType.AUDIO,
                request.getPayload().sdp());

        String messageJson = messageFactory.createWebSocketCallOfferMessage(
                callId,
                fromUser.getId(),
                request.getPayload().sdp(),
                CallType.AUDIO.toString()
        );
        TextWebSocketFrame frame = new TextWebSocketFrame(messageJson);
        Channel targetChannel = getClientChannel(request.getRecipientId());

        if (targetChannel.isActive()) {
            targetChannel.writeAndFlush(frame.copy());
        }
    }

    public void sendCallResponse(WebSocketMessageIn<IncomingCallResponsePayload> response) {
        IncomingCallResponsePayload payload = response.getPayload();
        Channel targetChannel = getClientChannel(response.getRecipientId());

        callService.updateCallStatus(response.getPayload().callId(), CallStatus.ACCEPTED);

        CallResponsePayload outPayload = new CallResponsePayload(
                payload.callId(),
                payload.callerId(),
                payload.response()
        );

        String messageJson = messageFactory.createWebSocketCallResponseMessageWithPayload(
                response.getRecipientId(),
                outPayload
        );
        TextWebSocketFrame frame = new TextWebSocketFrame(messageJson);
        if (targetChannel.isActive()) {
            targetChannel.writeAndFlush(frame.copy());
        }
    }

    public void sendCallCandidate(WebSocketMessageIn<IncomingCallCandidatePayload> message) {
        IncomingCallCandidatePayload payload = message.getPayload();
        Channel targetChannel = getClientChannel(message.getRecipientId());

        String messageJson = messageFactory.createWebSocketCallCandidateMessage(
                message.getRecipientId(),
                payload
        );
        TextWebSocketFrame frame = new TextWebSocketFrame(messageJson);
        if (targetChannel.isActive()) {
            targetChannel.writeAndFlush(frame.copy());
        }
    }

    public void sendCallEnd(WebSocketMessageIn<IncomingCallEndPayload> message) {
        Channel targetChannel = getClientChannel(message.getRecipientId());

        callService.endCall(message.getPayload().callId(), message.getPayload().callDuration());

        String messageJson = messageFactory.createWebSocketCallEndMessage(message.getRecipientId());
        TextWebSocketFrame frame = new TextWebSocketFrame(messageJson);
        if (targetChannel.isActive()) {
            targetChannel.writeAndFlush(frame.copy());
        }
    }

    public void clearAllClients() {
        clients.clear();
    }

    private void deliverMessageToUser(DirectMessage directMessage) {
        Channel targetChannel = getClientChannel(directMessage.getToUserId());
        messageService.saveDirectMessage(directMessage);

        if (targetChannel == null) {
            log.debug("User {} is not connected, storing message for later", directMessage.getToUserId());
            // TODO: Send push notification for offline user
            return;
        }

        String messageJson = messageFactory.createWebSocketChatMessage(directMessage);
        TextWebSocketFrame frame = new TextWebSocketFrame(messageJson);

        if (targetChannel.isActive()) {
            targetChannel.writeAndFlush(frame.copy());
        } else {
            log.debug("Channel inactive for user {}", directMessage.getToUserId());
            // TODO: Send push notification for offline user
        }

        frame.release();
    }
}
