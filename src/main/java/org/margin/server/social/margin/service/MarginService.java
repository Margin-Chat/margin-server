package org.margin.server.social.margin.service;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.users.api.UserLookup;
import org.margin.server.social.api.MarginIconCommands;
import org.margin.server.social.api.MarginSubscriptionPolicy;
import org.margin.server.social.margin.events.UserAddedToMarginEvent;
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
import org.margin.server.users.exceptions.UserNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
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
public class MarginService {

    private final MarginRepository marginRepository;
    private final MarginIconCommands marginIconCommands;
    private final MarginMemberRepository marginMemberRepository;
    private final SpacesService spacesService;
    private final MarginMapper marginMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final MarginSubscriptionPolicy marginSubscriptionPolicy;
    private final UserLookup userLookup;

    public MarginService(MarginRepository marginRepository,
                         MarginIconCommands marginIconCommands,
                         MarginMemberRepository marginMemberRepository,
                         SpacesService spacesService,
                         MarginMapper marginMapper,
                         ApplicationEventPublisher eventPublisher,
                         MarginSubscriptionPolicy marginSubscriptionPolicy,
                         UserLookup userLookup) {
        this.marginRepository = marginRepository;
        this.marginIconCommands = marginIconCommands;
        this.marginMemberRepository = marginMemberRepository;
        this.spacesService = spacesService;
        this.marginMapper = marginMapper;
        this.eventPublisher = eventPublisher;
        this.marginSubscriptionPolicy = marginSubscriptionPolicy;
        this.userLookup = userLookup;
    }

    @Transactional(readOnly = true)
    public Margin getById(Long marginId) {
        return marginRepository.findById(marginId)
                .orElseThrow(() -> new MarginNotFoundException(marginId));
    }

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
                                  Long userId) {

        String iconUrl = null;
        if (marginIcon != null && !marginIcon.isEmpty()) {
            iconUrl = marginIconCommands.save(marginIcon);
        }

        Margin margin = new Margin();
        margin.setName(name);
        margin.setDescription(description);
        margin.setVisibility(visibility);
        margin.setIconUrl(iconUrl);
        margin = marginRepository.save(margin);

        marginSubscriptionPolicy.onMarginCreated(margin.getId());

        addUserToMargin(margin.getId(), userId, MarginRole.OWNER, userId, true);

        spacesService.createNewSpace(
                new CreateSpaceDTO(
                        "General Space",
                        "A space for general organization",
                        Visibility.PUBLIC,
                        margin.getId()
                ),
                userId,
                margin
        );

        return marginMapper.marginToDto(margin);
    }

    @Transactional
    public Set<MarginDTO> getMarginsForUser(Long userId) {
        List<MarginMember> memberships = marginMemberRepository.findMarginMembersByUser(userId);
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
                                        Long addingUserId,
                                        boolean isNewlyCreated) {

        Margin margin = getById(marginId);

        marginSubscriptionPolicy.validateAddMarginMember(marginId);

        MarginMember member = margin.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId))
                .findFirst()
                .orElseGet(() -> {
                    MarginMember m = new MarginMember();
                    m.setUserId(userId);
                    m.setMargin(margin);
                    m.setRole(role);
                    m.setJoinedAt(Instant.now());
                    margin.getMembers().add(m);
                    return marginMemberRepository.save(m);
                });

        if (!isNewlyCreated) {
            eventPublisher.publishEvent(new UserAddedToMarginEvent(userId, addingUserId, marginId));
        }

        marginSubscriptionPolicy.notifyIfApproachingMemberLimit(marginId);

        return member;
    }

    @Transactional
    public MarginMemberDTO updateMarginMemberRole(Long marginId, Long requesterId, MarginMemberDTO memberDTO) {

        Margin margin = getById(marginId);

        MarginMember requester = margin.getMembers().stream()
                .filter(m -> m.getUserId().equals(requesterId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this margin"));

        if (requesterId.equals(memberDTO.user().id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot change your own role");
        }

        MarginMember target = margin.getMembers().stream()
                .filter(m -> m.getUserId().equals(memberDTO.user().id()))
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
        if (memberDTO.role() == MarginRole.OWNER) {
            requester.setRole(MarginRole.ADMIN);
        }
        marginRepository.save(margin);

        return new MarginMemberDTO(
                userLookup.dtoOf(target.getUserId()),
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
            margin.setIconUrl(marginIconCommands.save(icon));
        }

        Margin saved = marginRepository.save(margin);
        return marginMapper.marginToDto(saved);
    }

    @Transactional
    public void removeMarginMember(Long marginId, Long userId) {

        Margin margin = getById(marginId);

        MarginMember member = marginMemberRepository.findByUserIdAndMarginId(userId, marginId)
                .orElseThrow(UserNotFoundException::new);

        validateMemberIsNotTheLastAdmin(member, margin.getMembers());

        spacesService.removeUserFromSpaces(userId, margin);

        margin.getMembers().removeIf(m -> m.getUserId().equals(userId));
        marginRepository.save(margin);
    }

    public Optional<MarginMember> findMember(Long userId, Long marginId) {
        return marginMemberRepository.findByUserIdAndMarginId(userId, marginId);
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

    public boolean isUserMember(Long margin, Long targetUserId) {
        return marginMemberRepository.existsByMarginIdAndUserId(margin, targetUserId);
    }

    private void validateMemberIsNotTheLastAdmin(MarginMember member, List<MarginMember> members) {
        boolean hasOtherAdmin = members.stream()
                .anyMatch(m -> !m.getUserId().equals(member.getUserId())
                        && (m.getRole() == MarginRole.ADMIN || m.getRole() == MarginRole.OWNER));

        if (!hasOtherAdmin) {
            throw new RuntimeException("At least one admin required.");
        }
    }
}
