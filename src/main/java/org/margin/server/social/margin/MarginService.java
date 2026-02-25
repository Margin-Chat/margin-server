package org.margin.server.social.margin;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.margin.models.MarginMember;
import org.margin.server.social.margin.models.MarginRole;
import org.margin.server.social.margin.models.dtos.MarginDTO;
import org.margin.server.social.models.Visibility;
import org.margin.server.social.margin.models.Margin;
import org.margin.server.storage.StorageService;
import org.margin.server.users.exceptions.UserNotFoundException;
import org.margin.server.users.models.User;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MarginService {

    private final MarginRepository marginRepository;
    private final StorageService storageService;
    private final MarginMemberRepository marginMemberRepository;

    public MarginService(MarginRepository marginRepository, StorageService storageService, MarginMemberRepository marginMemberRepository) {
        this.marginRepository = marginRepository;
        this.storageService = storageService;
        this.marginMemberRepository = marginMemberRepository;
    }

    public Margin getMargin(Long marginId) {
        return marginRepository.findById(marginId).orElseThrow(() -> new MarginNotFoundException(marginId));
    }

    public void createMargin(String name, String description, Visibility visibility, MultipartFile marginIcon) {
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
    }

    public Set<MarginDTO> getMarginsForUser(User user) {
        List<MarginMember> marginMembersByUser = marginMemberRepository.findMarginMembersByUser(user.getId());
        if (marginMembersByUser.isEmpty()) {
            throw new UserNotFoundException(user.getId());
        }

        return marginMembersByUser.stream()
                .map(MarginMember::getMargin)
                .map(MarginDTO::new)
                .collect(Collectors.toSet());
    }

    @Transactional
    public void addUsersToMargin(Long marginId, List<User> users) {
        Margin margin = marginRepository.findById(marginId).orElseThrow(() -> new MarginNotFoundException(marginId));

        Set<User> existingUsers = margin.getMembers().stream()
                .map(MarginMember::getUser)
                .collect(Collectors.toSet());

        Set<User> usersToAdd = users.stream()
                .filter(user -> !existingUsers.contains(user))
                .collect(Collectors.toSet());

        List<MarginMember> marginMembers = new ArrayList<>();
        usersToAdd.forEach(user -> {
            MarginMember marginMember = new MarginMember();
            marginMember.setUser(user);
            marginMember.setMargin(margin);
            marginMember.setRole(MarginRole.MEMBER);
            marginMembers.add(marginMember);
        });
        marginMemberRepository.saveAll(marginMembers);
    }
}
