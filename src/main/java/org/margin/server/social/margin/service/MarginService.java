package org.margin.server.social.margin.service;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.notifications.NotificationType;
import org.margin.server.notifications.services.NotificationService;
import org.margin.server.social.margin.MarginLookup;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.entities.MarginMember;
import org.margin.server.social.margin.exceptions.MarginNotFoundException;
import org.margin.server.social.margin.models.MarginRole;
import org.margin.server.social.margin.models.dtos.MarginDTO;
import org.margin.server.social.margin.models.dtos.MarginMemberDTO;
import org.margin.server.social.margin.models.dtos.UpdateMarginDTO;
import org.margin.server.social.margin.repositories.MarginMemberRepository;
import org.margin.server.social.margin.repositories.MarginRepository;
import org.margin.server.social.models.Visibility;
import org.margin.server.social.space.models.dtos.CreateSpaceDTO;
import org.margin.server.social.space.services.SpacesService;
import org.margin.server.storage.StorageService;
import org.margin.server.subscriptions.models.SubscriptionTier;
import org.margin.server.subscriptions.services.SubscriptionService;
import org.margin.server.subscriptions.services.SubscriptionValidationService;
import org.margin.server.users.exceptions.UserNotFoundException;
import org.margin.server.users.models.User;
import org.margin.server.users.services.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MarginService implements MarginLookup {

    private final MarginRepository marginRepository;
    private final StorageService storageService;
    private final MarginMemberRepository marginMemberRepository;
    private final UserService userService;
    private final SpacesService spacesService;
    private final MarginMapper marginMapper;
    private final NotificationService notificationService;
    private final SubscriptionService subscriptionService;
    private final SubscriptionValidationService subscriptionValidationService;

    public MarginService(MarginRepository marginRepository,
                         StorageService storageService,
                         MarginMemberRepository marginMemberRepository,
                         UserService userService,
                         SpacesService spacesService,
                         MarginMapper marginMapper,
                         NotificationService notificationService,
                         SubscriptionService subscriptionService,
                         SubscriptionValidationService subscriptionValidationService) {
        this.marginRepository = marginRepository;
        this.storageService = storageService;
        this.marginMemberRepository = marginMemberRepository;
        this.userService = userService;
        this.spacesService = spacesService;
        this.marginMapper = marginMapper;
        this.notificationService = notificationService;
        this.subscriptionService = subscriptionService;
        this.subscriptionValidationService = subscriptionValidationService;
    }

    @Override
    @Transactional(readOnly = true)
    public Margin getById(Long marginId) {
        return marginRepository.findById(marginId)
                .orElseThrow(() -> new MarginNotFoundException(marginId));
    }

    @Override
    @Transactional(readOnly = true)
    public Margin findByIconFileName(String fileName) {
        return marginRepository.findFirstByIconUrlEndsWith("/margin-icons/" + fileName)
                .orElseThrow(() -> new MarginNotFoundException(fileName));
    }

    @Transactional
    public MarginDTO getMarginAsDto(Long marginId) {
        return marginMapper.marginToDto(getById(marginId));
    }

    @Transactional
    public MarginDTO createMargin(String name,
                                  String description,
                                  Visibility visibility,
                                  MultipartFile marginIcon,
                                  User user) {

        String iconUrl = null;
        if (marginIcon != null && !marginIcon.isEmpty()) {
            iconUrl = storageService.saveMarginIcon(marginIcon);
        }

        Margin margin = new Margin();
        margin.setName(name);
        margin.setDescription(description);
        margin.setVisibility(visibility);
        margin.setIconUrl(iconUrl);
        margin = marginRepository.save(margin);

        subscriptionService.createSubscriptionForMargin(margin, SubscriptionTier.FREE);

        addUserToMargin(margin.getId(), user.getId(), MarginRole.OWNER, user, true);

        spacesService.createNewSpace(
                new CreateSpaceDTO(
                        "General Space",
                        "A space for general organization",
                        Visibility.PUBLIC,
                        margin.getId()
                ),
                user,
                margin
        );

        return marginMapper.marginToDto(margin);
    }

    @Transactional
    public Set<MarginDTO> getMarginsForUser(User user) {
        List<MarginMember> memberships = marginMemberRepository.findMarginMembersByUser(user.getId());
        if (memberships.isEmpty()) return Collections.emptySet();

        return memberships.stream()
                .map(MarginMember::getMargin)
                .map(marginMapper::marginToDto)
                .collect(Collectors.toSet());
    }

    @Transactional
    public MarginMember addUserToMargin(Long marginId,
                                        Long userId,
                                        MarginRole role,
                                        User addingUser,
                                        boolean isNewlyCreated) {

        Margin margin = getById(marginId);
        User user = userService.getById(userId);

        subscriptionValidationService.validateAddMarginMember(margin);

        MarginMember member = margin.getMembers().stream()
                .filter(m -> m.getUser().getId().equals(userId))
                .findFirst()
                .orElseGet(() -> {
                    MarginMember m = new MarginMember();
                    m.setUser(user);
                    m.setMargin(margin);
                    m.setRole(role);
                    m.setJoinedAt(Instant.now());
                    margin.getMembers().add(m);
                    return marginMemberRepository.save(m);
                });

        if (!isNewlyCreated) {
            notificationService.createForUsers(
                    Collections.singletonList(user),
                    addingUser,
                    NotificationType.ADDED_TO_MARGIN,
                    null,
                    marginId
            );
        }

        subscriptionValidationService.notifyIfApproachingMemberLimit(margin);

        return member;
    }

    @Transactional
    public MarginMemberDTO updateMarginMemberRole(Long marginId, Long requesterId, MarginMemberDTO memberDTO) {

        Margin margin = getById(marginId);

        MarginMember requester = margin.getMembers().stream()
                .filter(m -> m.getUser().getId().equals(requesterId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this margin"));

        if (requesterId.equals(memberDTO.user().id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot change your own role");
        }

        MarginMember target = margin.getMembers().stream()
                .filter(m -> m.getUser().getId().equals(memberDTO.user().id()))
                .findFirst()
                .orElseThrow(UserNotFoundException::new);

        if (target.getRole().getRank() <= requester.getRole().getRank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot change the role of a member with equal or higher rank");
        }

        if (memberDTO.role().getRank() < requester.getRole().getRank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot assign a role with higher authority than your own");
        }

        if (target.getRole() == MarginRole.ADMIN || target.getRole() == MarginRole.OWNER) {
            validateMemberIsNotTheLastAdmin(target, margin.getMembers());
        }

        target.setRole(memberDTO.role());
        marginRepository.save(margin);

        return new MarginMemberDTO(
                new org.margin.server.users.models.dtos.UserDTO(target.getUser(), false),
                target.getRole(),
                target.getJoinedAt()
        );
    }

    @Transactional
    public MarginDTO updateMarginAsDto(UpdateMarginDTO updateMarginDTO, MultipartFile icon) {

        Margin margin = getById(updateMarginDTO.marginId());

        margin.setName(updateMarginDTO.marginName());
        margin.setDescription(updateMarginDTO.description());

        if (icon != null && !icon.isEmpty()) {
            margin.setIconUrl(storageService.saveMarginIcon(icon));
        }

        Margin saved = marginRepository.save(margin);
        return marginMapper.marginToDto(saved);
    }

    @Transactional
    public void removeMarginMember(Long marginId, Long userId) {

        Margin margin = getById(marginId);

        MarginMember member = marginMemberRepository.findByUser_IdAndMargin_Id(userId, marginId)
                .orElseThrow(UserNotFoundException::new);

        validateMemberIsNotTheLastAdmin(member, margin.getMembers());

        spacesService.removeUserFromSpaces(userId, margin);

        margin.getMembers().removeIf(m -> m.getUser().getId().equals(userId));
        marginRepository.save(margin);
    }

    public Optional<MarginMember> findMember(Long userId, Long marginId) {
        return marginMemberRepository.findByUser_IdAndMargin_Id(userId, marginId);
    }

    @Transactional(readOnly = true)
    public MarginMember getOwner(Long marginId) {
        return marginMemberRepository.findByMargin_IdAndRole(marginId, MarginRole.OWNER)
                .orElseThrow(() -> new IllegalStateException("Margin " + marginId + " has no owner"));
    }

    @Transactional
    public void deleteMargin(Long marginId) {
        Margin margin = getById(marginId);
        Instant now = Instant.now();

        margin.getSpaces().forEach(space -> {
            space.getChannels().forEach(channel -> {
                if (channel.getConversation() != null) {
                    channel.getConversation().setDeletedAt(now);
                }
                channel.setDeletedAt(now);
            });
            space.setDeletedAt(now);
        });

        marginMemberRepository.deleteAll(margin.getMembers());
        margin.getMembers().clear();
        margin.setDeletedAt(now);
        marginRepository.save(margin);

        log.info("Soft deleted margin with id {}", marginId);
    }

    public boolean isUserMember(Long margin, User targetUser) {
        return marginMemberRepository.existsByMarginIdAndUserId(margin, targetUser.getId());
    }

    private void validateMemberIsNotTheLastAdmin(MarginMember member, List<MarginMember> members) {
        boolean hasOtherAdmin = members.stream()
                .anyMatch(m -> !m.getUser().getId().equals(member.getUser().getId())
                        && (m.getRole() == MarginRole.ADMIN || m.getRole() == MarginRole.OWNER));

        if (!hasOtherAdmin) {
            throw new RuntimeException("At least one admin required.");
        }
    }
}
