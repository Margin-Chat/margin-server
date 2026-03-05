package org.margin.server.social.margin;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.connection.ConnectionManager;
import org.margin.server.social.channel.channel.ChannelDTO;
import org.margin.server.social.margin.models.MarginMember;
import org.margin.server.social.margin.models.MarginRole;
import org.margin.server.social.margin.models.dtos.MarginDTO;
import org.margin.server.social.margin.models.dtos.MarginMemberDTO;
import org.margin.server.social.margin.models.dtos.UpdateMarginDTO;
import org.margin.server.social.margin.repositories.MarginMemberRepository;
import org.margin.server.social.margin.repositories.MarginRepository;
import org.margin.server.social.models.Visibility;
import org.margin.server.social.margin.models.Margin;
import org.margin.server.social.space.models.dtos.SpaceDTO;
import org.margin.server.social.space.models.dtos.SpaceMemberDTO;
import org.margin.server.storage.StorageService;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.users.services.UserService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MarginService {

    private final MarginRepository marginRepository;
    private final StorageService storageService;
    private final MarginMemberRepository marginMemberRepository;
    private final ConnectionManager connectionManager;
    private final UserService userService;

    public MarginService(MarginRepository marginRepository,
                         StorageService storageService,
                         MarginMemberRepository marginMemberRepository,
                         ConnectionManager connectionManager, UserService userService) {
        this.marginRepository = marginRepository;
        this.storageService = storageService;
        this.marginMemberRepository = marginMemberRepository;
        this.connectionManager = connectionManager;
        this.userService = userService;
    }

    public Margin getById(Long marginId) {
        return marginRepository.findById(marginId).orElseThrow(() -> new MarginNotFoundException(marginId));
    }

    @Transactional
    public void createMargin(String name, String description, Visibility visibility, MultipartFile marginIcon, User user) {
        String marginIconUrl = null;

        if (marginIcon != null && !marginIcon.isEmpty()) {
            marginIconUrl = storageService.saveMarginIcon(marginIcon);
        }

        Margin margin = new Margin();
        margin.setName(name);
        margin.setDescription(description);
        margin.setVisibility(visibility);
        margin.setIconUrl(marginIconUrl);
        marginRepository.save(margin);

        log.info("Created new Margin with id {}", margin.getId());

        addUserToMargin(margin.getId(), user.getId(), MarginRole.ADMIN);

        log.info("Added user {} as Admin to margin with id {}", user.getId(), margin.getId());
    }

    public Set<MarginDTO> getMarginsForUser(User user) {
        List<MarginMember> marginMembersByUser = marginMemberRepository.findMarginMembersByUser(user.getId());
        if (marginMembersByUser.isEmpty()) {
            return Collections.emptySet();
        }

        return marginMembersByUser.stream()
                .map(MarginMember::getMargin)
                .map(this::toDTO)
                .collect(Collectors.toSet());
    }

    @Transactional
    public MarginMember addUserToMargin(Long marginId, Long userId, MarginRole role) {
        Margin margin = marginRepository.findById(marginId)
                .orElseThrow(() -> new MarginNotFoundException(marginId));

        User user = userService.getById(userId);

        return margin.getMembers().stream()
                .filter(m -> m.getUser().getId().equals(user.getId()))
                .findFirst()
                .orElseGet(() -> {
                    MarginMember member = new MarginMember();
                    member.setUser(user);
                    member.setMargin(margin);
                    member.setRole(role);
                    member.setJoinedAt(LocalDateTime.now());
                    return marginMemberRepository.save(member);
                });
    }

    public Margin updateMargin(UpdateMarginDTO updateMarginDTO) {
        Margin margin = getById(updateMarginDTO.marginId());
        margin.setName(updateMarginDTO.marginName());
        margin.setDescription(updateMarginDTO.description());
        return marginRepository.save(margin);
    }

    public void deleteMargin(Long marginId) {
        Margin margin = getById(marginId);
        marginMemberRepository.deleteAll(margin.getMembers());
        marginRepository.deleteById(marginId);
    }

    public MarginMemberDTO memberToDto(MarginMember marginMember) {
        return new MarginMemberDTO(
                new UserDTO(
                        marginMember.getUser(),
                        connectionManager.isUserOnline(marginMember.getUser().getId())),
                marginMember.getRole(),
                marginMember.getJoinedAt());
    }

    public MarginMemberDTO updateMarginMemberRole(Long marginId, MarginMemberDTO memberDTO) {
        Margin margin = getById(marginId);
        margin.getMembers().stream()
                .filter(m -> m.getUser().getId().equals(memberDTO.user().id()))
                .findFirst()
                .ifPresent(m -> m.setRole(memberDTO.role()));
        marginRepository.save(margin);
        return memberDTO;
    }

    public void removeMarginMember(Long marginId, Long userId) {
        Margin margin = getById(marginId);
        margin.getMembers().removeIf(m -> m.getUser().getId().equals(userId));
        marginRepository.save(margin);
    }

    public MarginDTO toDTO(Margin margin) {
        List<MarginMemberDTO> members = margin.getMembers().stream()
                .map(m -> new MarginMemberDTO(
                        new UserDTO(m.getUser(), connectionManager.isUserOnline(m.getUser().getId())),
                        m.getRole(),
                        m.getJoinedAt()
                ))
                .toList();

        List<SpaceDTO> spaces = margin.getSpaces().stream()
                .map(space -> {
                    List<SpaceMemberDTO> spaceMembers = space.getMembers() != null
                            ? space.getMembers().stream()
                            .map(m -> new SpaceMemberDTO(
                                    new UserDTO(m.getUser(), connectionManager.isUserOnline(m.getUser().getId())),
                                    m.getSpace().getId(),
                                    m.getRole(),
                                    m.getJoinedAt()
                            ))
                            .toList()
                            : List.of();

                    List<ChannelDTO> channels = space.getChannels() != null
                            ? space.getChannels().stream().map(ChannelDTO::new).toList()
                            : List.of();

                    return new SpaceDTO(space.getId(), space.getName(), space.getDescription(), space.getVisibility(), channels, spaceMembers);
                })
                .toList();

        return new MarginDTO(margin.getId(), margin.getName(), margin.getDescription(), margin.getVisibility(), margin.getIconUrl(), members, spaces);
    }
}
