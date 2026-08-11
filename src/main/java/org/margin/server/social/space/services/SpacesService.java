package org.margin.server.social.space.services;

import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.users.api.UserLookup;
import org.margin.server.presence.PresenceService;
import org.margin.server.social.channel.services.ChannelService;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.entities.MarginMember;
import org.margin.server.social.margin.repositories.MarginMemberRepository;
import org.margin.server.social.margin.service.MarginMapper;
import org.margin.server.social.models.Visibility;
import org.margin.server.social.space.exceptions.SpaceNotFoundException;
import org.margin.server.social.space.models.Space;
import org.margin.server.social.space.models.SpaceMember;
import org.margin.server.social.space.models.SpaceRole;
import org.margin.server.social.space.models.dtos.CreateSpaceDTO;
import org.margin.server.social.space.models.dtos.SpaceDTO;
import org.margin.server.social.space.models.dtos.SpaceMemberDTO;
import org.margin.server.shared.authorization.ChannelAudience;
import org.margin.server.shared.voice.VoiceParticipantLookup;
import org.margin.server.social.channel.models.ChannelDTO;
import org.margin.server.social.space.repositories.SpaceMemberRepository;
import org.margin.server.social.space.repositories.SpacesRepository;
import org.margin.server.users.models.dtos.UserDTO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SpacesService implements ChannelAudience {
    private final SpacesRepository spacesRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final ChannelService channelService;
    private final MarginMemberRepository marginMemberRepository;
    private final SpacesCreationService spacesCreationService;
    private final MarginMapper marginMapper;
    private final PresenceService presenceService;
    private final SpacesActions spacesActions;
    private final UserLookup userLookup;
    private final VoiceParticipantLookup voiceParticipantLookup;

    public SpacesService(SpacesRepository spacesRepository,
                         SpaceMemberRepository spaceMemberRepository,
                         ChannelService channelService,
                         MarginMemberRepository marginMemberRepository,
                         SpacesCreationService spacesCreationService,
                         MarginMapper marginMapper,
                         PresenceService presenceService,
                         SpacesActions spacesActions,
                         UserLookup userLookup,
                         VoiceParticipantLookup voiceParticipantLookup) {
        this.spacesRepository = spacesRepository;
        this.spaceMemberRepository = spaceMemberRepository;
        this.channelService = channelService;
        this.marginMemberRepository = marginMemberRepository;
        this.spacesCreationService = spacesCreationService;
        this.marginMapper = marginMapper;
        this.presenceService = presenceService;
        this.spacesActions = spacesActions;
        this.userLookup = userLookup;
        this.voiceParticipantLookup = voiceParticipantLookup;
    }

    @Transactional(readOnly = true)
    public List<SpaceDTO> getSpacesForMargin(Long marginId) {
        return spacesRepository.findByMargin_Id(marginId).stream()
                .map(marginMapper::spaceToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SpaceDTO> getSpacesForUserInMargin(Long userId, Long marginId) {
        List<SpaceDTO> spaces = spacesRepository.findVisibleSpacesForUser(userId, marginId).stream()
                .map(marginMapper::spaceToDto)
                .toList();

        return withVoiceParticipants(spaces);
    }

    private List<SpaceDTO> withVoiceParticipants(List<SpaceDTO> spaces) {
        List<Long> channelIds = spaces.stream()
                .flatMap(space -> space.channels().stream())
                .map(ChannelDTO::id)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, List<Long>> participantIds = voiceParticipantLookup.participantIdsByChannel(channelIds);
        if (participantIds.isEmpty()) {
            return spaces;
        }

        Map<Long, UserDTO> participants = userLookup.dtosOf(participantIds.values().stream()
                        .flatMap(List::stream)
                        .distinct()
                        .toList()).stream()
                .collect(Collectors.toMap(UserDTO::id, Function.identity(), (first, second) -> first));

        return spaces.stream()
                .map(space -> space.withChannels(space.channels().stream()
                        .map(channel -> channel.withVoiceParticipants(
                                participantIds.getOrDefault(channel.id(), List.of()).stream()
                                        .map(participants::get)
                                        .filter(Objects::nonNull)
                                        .toList()))
                        .toList()))
                .toList();
    }

    @Transactional
    public SpaceDTO createNewSpace(CreateSpaceDTO dto, Long userId, Margin margin) {
        Optional<Space> spaceByName = spacesRepository.getSpaceByName(dto.name(), margin.getId());
        if (spaceByName.isPresent()) {
            throw new DuplicateKeyException("Space name already exists");
        }

        Space space = spacesCreationService.create(dto.name(), dto.description(), dto.visibility(), margin);
        channelService.createNewChannel(space, "General Chat", "A channel for general conversation");
        spacesActions.addUserToSpace(userId, space, SpaceRole.ADMIN);

        if (dto.visibility() == Visibility.PUBLIC) {
            marginMemberRepository.findByMargin_Id(margin.getId()).stream()
                    .map(MarginMember::getUserId)
                    .filter(memberId -> !memberId.equals(userId))
                    .forEach(memberId -> spacesActions.addUserToSpace(memberId, space, SpaceRole.MEMBER));
        }

        return marginMapper.spaceToDto(space);
    }

    @Transactional(readOnly = true)
    public @NotNull Space getById(Long id) {
        return spacesRepository.findById(id)
                .orElseThrow(() -> new SpaceNotFoundException(id));
    }

    @Transactional
    public void addUsersToDefaultSpacesForMargin(Long marginId, Long userId) {
        List<Space> publicSpaces = spacesRepository.findByMarginIdAndVisibility(marginId, Visibility.PUBLIC);
        for (Space space : publicSpaces) {
            if (!spaceMemberRepository.existsSpaceMemberByUserAndSpace(userId, space.getId())) {
                spacesActions.addUserToSpace(userId, space, SpaceRole.MEMBER);
            }
        }
    }

    @Transactional
    public SpaceMemberDTO addNewUserToSpace(Long userId, Long spaceId, SpaceRole role) {
        Space space = spacesRepository.findById(spaceId)
                .orElseThrow(() -> new SpaceNotFoundException(spaceId));

        SpaceMember spaceMember = spacesActions.addUserToSpace(userId, space, role);

        return new SpaceMemberDTO(
                userLookup.dtoOf(spaceMember.getUserId()),
                spaceMember.getSpace().getId(),
                spaceMember.getRole(),
                spaceMember.getJoinedAt()
        );
    }

    @Transactional
    public SpaceDTO updateSpace(SpaceDTO spaceDTO) {
        Space existing = spacesRepository.findById(spaceDTO.spaceId())
                .orElseThrow(() -> new SpaceNotFoundException(spaceDTO.spaceId()));
        boolean switchingToPublic = existing.getVisibility() == Visibility.PRIVATE
                && spaceDTO.visibility() == Visibility.PUBLIC;

        Space saved = spacesRepository.save(spacesActions.updateSpace(spaceDTO));

        if (switchingToPublic) {
            marginMemberRepository.findByMargin_Id(saved.getMargin().getId()).stream()
                    .map(MarginMember::getUserId)
                    .filter(id -> !spaceMemberRepository.existsSpaceMemberByUserAndSpace(id, saved.getId()))
                    .forEach(id -> spacesActions.addUserToSpace(id, saved, SpaceRole.MEMBER));
        }

        return marginMapper.spaceToDto(saved);
    }

    @Transactional
    public void deleteSpace(Long spaceId) {
        spacesActions.deleteSpace(spaceId);
    }

    @Transactional
    public SpaceMemberDTO updateSpaceMemberRole(Space space, SpaceMemberDTO spaceMemberDTO) {
        SpaceMember saved = spaceMemberRepository.save(spacesActions.prepareRoleUpdate(space, spaceMemberDTO));
        return new SpaceMemberDTO(
                userLookup.dtoOf(saved.getUserId()),
                saved.getSpace().getId(),
                saved.getRole(),
                saved.getJoinedAt()
        );
    }

    @Transactional
    public void removeUserFromSpaces(Long userId, List<Long> spaceIds) {
        List<Space> spaces = spaceIds.stream().map(this::getById).toList();
        spacesActions.removeUserFromSpaces(userId, spaces);
    }

    @Transactional
    public void removeUserFromSpaces(Long userId, Margin margin) {
        List<Space> spaces = spacesRepository.findByMargin_Id(margin.getId());
        spacesActions.removeUserFromSpaces(userId, spaces);
    }

    @Override
    public List<Long> memberIdsForChannel(Long channelId) {
        return spaceMemberRepository.findUserIdsByChannelId(channelId);
    }
}
