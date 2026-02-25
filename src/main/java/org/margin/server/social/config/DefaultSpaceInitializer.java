package org.margin.server.social.config;

import org.margin.server.social.conversation.ConversationMemberId;
import org.margin.server.social.margin.MarginService;
import org.margin.server.users.models.User;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.channel.ChannelService;
import org.margin.server.social.channel.channel.Channel;
import org.margin.server.social.conversation.Conversation;
import org.margin.server.social.conversation.ConversationMember;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.conversation.repositories.ConversationRepository;
import org.margin.server.social.margin.models.Margin;
import org.margin.server.social.space.models.Space;
import org.margin.server.social.space.models.dtos.CreateSpaceDTO;
import org.margin.server.social.models.Visibility;
import org.margin.server.social.channel.ChannelRepository;
import org.margin.server.social.margin.MarginRepository;
import org.margin.server.social.space.repositories.SpaceMemberRepository;
import org.margin.server.social.space.repositories.SpacesRepository;
import org.margin.server.social.space.services.SpacesService;
import org.margin.server.users.repositories.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class DefaultSpaceInitializer implements CommandLineRunner {

    private final SpacesRepository spacesRepository;
    private final ChannelRepository channelRepository;
    private final UserRepository userRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final SpacesService spacesService;
    private final MarginRepository marginRepository;
    private final ChannelService channelService;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MarginService marginService;

    public DefaultSpaceInitializer(SpacesRepository spacesRepository,
                                   ChannelRepository channelRepository,
                                   UserRepository userRepository,
                                   SpaceMemberRepository spaceMemberRepository,
                                   SpacesService spacesService,
                                   MarginRepository marginRepository,
                                   ChannelService channelService,
                                   ConversationRepository conversationRepository,
                                   ConversationMemberRepository conversationMemberRepository, MarginService marginService) {
        this.spacesRepository = spacesRepository;
        this.channelRepository = channelRepository;
        this.userRepository = userRepository;
        this.spaceMemberRepository = spaceMemberRepository;
        this.spacesService = spacesService;
        this.marginRepository = marginRepository;
        this.channelService = channelService;
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.marginService = marginService;
    }

    @Override
    public void run(String... args) {
        initializeDefaultSpace();
        initializeMembers();
    }

    @Transactional
    public void initializeDefaultSpace() {
        if (!marginRepository.existsMarginByName("Margin")) {

            Margin defaultMargin = new Margin();
            defaultMargin.setName("Margin");
            defaultMargin.setDescription("Default Margin");
            defaultMargin.setVisibility(Visibility.PUBLIC);
            marginRepository.save(defaultMargin);

            CreateSpaceDTO generalSpace = new CreateSpaceDTO(
                    "General",
                    "Default general space for all users",
                    Visibility.PUBLIC,
                    defaultMargin.getId());
            Space newSpace = spacesService.createNewSpace(generalSpace);

            channelService.createChannel(newSpace.getId(), "General Chat", "General text chat");
        }
    }

    @Transactional
    public void initializeMembers() {
        List<Margin> margin = marginRepository.findByName("Margin");
        if (margin.isEmpty()) {
            return;
        }
        List<Space> spaceByMargin = spacesRepository.getSpaceByMargin(margin.getFirst());
        List<Channel> channelsBySpaceId = channelRepository.getChannelsBySpaceId(spaceByMargin.getFirst().getId());
        Optional<Conversation> byChannelId = conversationRepository.findByChannelId(channelsBySpaceId.getFirst().getId());

        if (byChannelId.isEmpty()) {
            return;
        }

        Conversation conversation = byChannelId.get();

        List<ConversationMember> members = new ArrayList<>();
        userRepository.findAll().forEach(user -> {
            ConversationMember member = new ConversationMember();

            ConversationMemberId id = new ConversationMemberId();
            id.setConversationId(conversation.getId());
            id.setUserId(user.getId());
            member.setId(id);

            member.setConversation(conversation);
            member.setUser(user);
            member.setJoinedAt(LocalDateTime.now());
            members.add(member);
        });
        conversationMemberRepository.saveAll(members);

        List<User> allUsers = userRepository.findAll();

        marginService.addUsersToMargin(margin.getFirst().getId(), allUsers);
        spacesService.addUsersToSpace(spaceByMargin.getFirst().getId(), allUsers);
    }
}