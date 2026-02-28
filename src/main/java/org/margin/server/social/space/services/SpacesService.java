package org.margin.server.social.space.services;

import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.margin.server.social.channel.ChannelRepository;
import org.margin.server.social.channel.channel.Channel;
import org.margin.server.social.conversation.ConversationMember;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.margin.MarginService;
import org.margin.server.social.margin.models.Margin;
import org.margin.server.social.space.exceptions.SpaceNotFoundException;
import org.margin.server.social.space.models.SpaceMember;
import org.margin.server.social.space.models.SpaceRole;
import org.margin.server.social.space.models.dtos.CreateSpaceDTO;
import org.margin.server.social.space.models.dtos.SpaceDTO;
import org.margin.server.social.space.models.dtos.SpaceMemberDTO;
import org.margin.server.social.space.repositories.SpaceMemberRepository;
import org.margin.server.users.exceptions.UserNotFoundException;
import org.margin.server.users.models.User;
import org.margin.server.users.repositories.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.margin.server.social.space.models.Space;
import org.margin.server.social.space.repositories.SpacesRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SpacesService {
    private final SpacesRepository spacesRepository;
    private final MarginService marginService;
    private final SpaceMemberRepository spaceMemberRepository;
    private final UserRepository userRepository;
    private final ChannelRepository channelRepository;
    private final ConversationMemberRepository conversationMemberRepository;

    public SpacesService(SpacesRepository spacesRepository, MarginService marginService, SpaceMemberRepository spaceMemberRepository, UserRepository userRepository, ChannelRepository channelRepository, ConversationMemberRepository conversationMemberRepository) {
        this.spacesRepository = spacesRepository;
        this.marginService = marginService;
        this.spaceMemberRepository = spaceMemberRepository;
        this.userRepository = userRepository;
        this.channelRepository = channelRepository;
        this.conversationMemberRepository = conversationMemberRepository;
    }

    public List<Space> getSpaces() {
        return spacesRepository.getSpaces()
                .stream()
                .toList();
    }

    public List<Space> getSpacesForUser(User user) {
        return spacesRepository.findByUser(user.getId());
    }

    @Transactional
    public Space createNewSpace(CreateSpaceDTO dto) {
        Optional<Space> spaceByName = spacesRepository.getSpaceByName(dto.name());

        if (spaceByName.isPresent()) {
            throw new DuplicateKeyException("Space name already exists");
        }

        Margin margin = marginService.getMargin(dto.marginId());

        Space newSpace = new Space();
        newSpace.setName(dto.name());
        newSpace.setDescription(dto.description());
        newSpace.setVisibility(dto.visibility());
        newSpace.setMargin(margin);
        spacesRepository.save(newSpace);

        log.info("Created space {}",  newSpace);

        return spacesRepository.findById(newSpace.getId()).orElseThrow();
    }

    public @NotNull Space getById(Long id) {
        return spacesRepository.findById(id).orElseThrow(() -> new RuntimeException("Space not found"));
    }

    private SpaceDTO toDto(Space space) {
        return new SpaceDTO(space);
    }

    @Transactional
    public void addUsersToSpace(Long spaceId, List<User> users) {
        Space space = spacesRepository.findById(spaceId).orElseThrow();

        Set<User> existingUsers = space.getMembers().stream()
                .map(SpaceMember::getUser)
                .collect(Collectors.toSet());

        List<SpaceMember> spaceMembers = users.stream()
                .filter(u -> !existingUsers.contains(u))
                .map(u -> createSpaceMember(u, space))
                .toList();

        spaceMemberRepository.saveAll(spaceMembers);
    }

    @Transactional
    public SpaceMember addNewSpaceMemberToSpace(SpaceMemberDTO spaceMemberDTO) {
        User user = userRepository.findById(spaceMemberDTO.user().id()).orElseThrow(() ->
                new UserNotFoundException(spaceMemberDTO.user().id()));

        Space space = spacesRepository.findById(spaceMemberDTO.spaceId()).orElseThrow(() ->
                new SpaceNotFoundException(spaceMemberDTO.spaceId()));

        if (spaceMemberRepository.existsSpaceMemberByUserAndSpace(user, space)) {
            throw new DuplicateKeyException("Can't add duplicate space member");
        }

        SpaceMember spaceMember = createSpaceMember(user, space);
        List<Channel> channels = spaceMember.getSpace().getChannels();
        for (Channel channel : channels) {
            ConversationMember conversationMember = new ConversationMember();
            conversationMember.setUser(user);
            conversationMember.setConversation(channel.getConversation());
            conversationMember.setJoinedAt(LocalDateTime.now());
            conversationMemberRepository.save(conversationMember);
        }
        return spaceMemberRepository.save(spaceMember);
    }

    @Transactional
    private @NonNull SpaceMember createSpaceMember(User u, Space space) {
        SpaceMember spaceMember = new SpaceMember();
        spaceMember.setSpace(space);
        spaceMember.setUser(u);
        spaceMember.setRole(SpaceRole.MEMBER);
        spaceMember.setJoinedAt(LocalDateTime.now());
        return spaceMember;
    }

    @Transactional
    public Space updateSpace(SpaceDTO spaceDTO) {
        Space space = spacesRepository.findById(spaceDTO.spaceId())
                .orElseThrow(() -> new SpaceNotFoundException(spaceDTO.spaceId()));

        space.setName(spaceDTO.spaceName());
        space.setDescription(spaceDTO.spaceDescription());
        return spacesRepository.save(space);
    }

    @Transactional
    public void deleteSpace(Long spaceId) {
        Space space = spacesRepository.findById(spaceId).orElseThrow(() -> new SpaceNotFoundException(spaceId));

        List<SpaceMember> spaceMemberBySpace = spaceMemberRepository.findSpaceMemberBySpace(space);
        spaceMemberRepository.deleteAll(spaceMemberBySpace);

        List<Channel> channelBySpace = channelRepository.findChannelBySpace(space);
        channelRepository.deleteAll(channelBySpace);

        spacesRepository.deleteById(spaceId);
    }
}