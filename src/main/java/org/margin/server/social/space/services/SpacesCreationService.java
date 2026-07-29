package org.margin.server.social.space.services;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.models.Visibility;
import org.margin.server.social.space.models.Space;
import org.margin.server.social.space.models.SpaceMember;
import org.margin.server.social.space.models.SpaceRole;
import org.margin.server.social.space.repositories.SpaceMemberRepository;
import org.margin.server.social.space.repositories.SpacesRepository;
import org.margin.server.users.models.User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;

@Service
@Slf4j
public class SpacesCreationService {
    private final SpacesRepository spacesRepository;
    private final SpaceMemberRepository spaceMemberRepository;

    public SpacesCreationService(SpacesRepository spacesRepository,
                                 SpaceMemberRepository spaceMemberRepository) {
        this.spacesRepository = spacesRepository;
        this.spaceMemberRepository = spaceMemberRepository;
    }

    @Transactional
    public Space create(String name, String description, Visibility visibility, Margin margin) {
        Space newSpace = new Space();
        newSpace.setName(name);
        newSpace.setDescription(description);
        newSpace.setVisibility(visibility);
        newSpace.setMargin(margin);
        Space space = spacesRepository.save(newSpace);
        log.info("Created space {}", space.getId());
        return space;
    }

    @Transactional
    public SpaceMember createMember(Long userId, Space space, SpaceRole role) {
        SpaceMember newSpaceMember = new SpaceMember();
        newSpaceMember.setSpace(space); // This sets the FK
        newSpaceMember.setUserId(userId);
        newSpaceMember.setRole(role);
        newSpaceMember.setJoinedAt(Instant.now());
        SpaceMember spaceMember = spaceMemberRepository.save(newSpaceMember);

        if (space.getMembers() == null) {
            space.setMembers(new ArrayList<>());
        }
        space.getMembers().add(spaceMember);

        log.info("Created space member {} with role {}", spaceMember.getId(), role);
        return spaceMember;
    }
}
