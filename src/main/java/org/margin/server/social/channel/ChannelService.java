package org.margin.server.social.channel;

import org.margin.server.social.channel.channel.Channel;
import org.margin.server.social.channel.channel.ChannelDTO;
import org.margin.server.social.channel.channel.ChannelType;
import org.margin.server.social.conversation.Conversation;
import org.margin.server.social.conversation.ConversationMember;
import org.margin.server.social.conversation.ConversationMemberId;
import org.margin.server.social.conversation.ConversationType;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.conversation.repositories.ConversationRepository;
import org.margin.server.social.exceptions.ChannelNotFoundException;
import org.margin.server.social.space.models.Space;
import org.margin.server.social.space.models.SpaceMember;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChannelService {
    private final ChannelRepository channelRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;

    public ChannelService(ChannelRepository channelRepository,
                          ConversationRepository conversationRepository,
                          ConversationMemberRepository conversationMemberRepository) {
        this.channelRepository = channelRepository;
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
    }

    public List<Channel> getChannelsForSpace(Long spaceId) {
        return channelRepository.getChannelsBySpaceId(spaceId);
    }

    @Transactional
    public Channel createChannel(Space space, String name, String description) {
        Channel channel = new Channel();
        channel.setSpace(space);
        channel.setName(name);
        channel.setDescription(description);
        channel.setChannelType(ChannelType.Communication);
        channel = channelRepository.save(channel);

        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.CHANNEL);
        conversation.setChannel(channel);
        conversation.setName(name);
        conversation.setCreatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        List<SpaceMember> members = space.getMembers();
        var conversationMembers = new ArrayList<ConversationMember>();
        for (SpaceMember member : members) {
            var conversationMember = new ConversationMember();
            conversationMember.setConversation(conversation);
            conversationMember.setUser(member.getUser());
            conversationMember.setJoinedAt(LocalDateTime.now());
            conversationMember.setId(new ConversationMemberId(
                    conversation.getId(),
                    member.getUser().getId()
            ));
            conversationMembers.add(conversationMember);
        }
        conversationMemberRepository.saveAll(conversationMembers);

        channel.setConversation(conversation);
        return channel;
    }

    @Transactional
    public Channel updateChannel(ChannelDTO channelDTO) {
        Channel channel = getChannel(channelDTO.id());
        channel.setName(channelDTO.name());
        channel.setDescription(channelDTO.description());
        channel.setChannelType(ChannelType.Communication);
        return channelRepository.save(channel);
    }

    @Transactional
    public void deleteChannel(Long channelId) {
        Channel channel = getChannel(channelId);

        Conversation conversation = channel.getConversation();
        conversationMemberRepository.deleteAll(conversationMemberRepository.findByConversation(conversation));
        conversationRepository.delete(conversation);
        channelRepository.delete(channel);
    }

    private Channel getChannel(Long id) {
        return channelRepository.findById(id).orElseThrow(() -> new ChannelNotFoundException(id));
    }
}