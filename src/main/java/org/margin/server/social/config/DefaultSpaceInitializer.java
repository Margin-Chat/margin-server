package org.margin.server.social.config;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.models.SpaceChannel;
import org.margin.server.social.models.space.Space;
import org.margin.server.social.models.space.SpaceDTO;
import org.margin.server.social.models.space.SpaceMember;
import org.margin.server.social.models.space.SpaceMemberId;
import org.margin.server.social.models.space.enums.SpaceMemberRole;
import org.margin.server.social.models.space.enums.SpaceVisibility;
import org.margin.server.social.repositories.SpaceChannelRepository;
import org.margin.server.social.repositories.SpaceMemberRepository;
import org.margin.server.social.repositories.SpaceRepository;
import org.margin.server.social.services.SpacesService;
import org.margin.server.users.models.User;
import org.margin.server.users.repositories.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class DefaultSpaceInitializer implements CommandLineRunner {

    private final SpaceRepository spaceRepository;
    private final SpaceChannelRepository spaceChannelRepository;
    private final UserRepository userRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final SpacesService spacesService;

    public DefaultSpaceInitializer(SpaceRepository spaceRepository,
                                   SpaceChannelRepository spaceChannelRepository,
                                   UserRepository userRepository,
                                   SpaceMemberRepository spaceMemberRepository,
                                   SpacesService spacesService) {
        this.spaceRepository = spaceRepository;
        this.spaceChannelRepository = spaceChannelRepository;
        this.userRepository = userRepository;
        this.spaceMemberRepository = spaceMemberRepository;
        this.spacesService = spacesService;
    }

    @Override
    public void run(String... args) {
        if (!spaceRepository.existsById(1L)) {

            SpaceDTO generalSpace = new SpaceDTO(
                    "General",
                    "Default general space for all users",
                    SpaceVisibility.PUBLIC);
            Space newSpace = spacesService.createNewSpace(generalSpace);

            log.info("Created default 'General' space");

            List<User> allUsers = userRepository.findAll();

            List<SpaceMember> members = new ArrayList<>();
            for (User user : allUsers) {
                SpaceMember spaceMember = new SpaceMember();
                spaceMember.setId(new SpaceMemberId(newSpace.getId(), user.getId()));
                spaceMember.setUser(user);
                spaceMember.setRole(SpaceMemberRole.MEMBER);
                spaceMember.setJoinedAt(LocalDateTime.now());

                members.add(spaceMember);
            }
            spaceMemberRepository.saveAll(members);

            SpaceChannel generalChannel = new SpaceChannel();
            generalChannel.setId(1L);
            generalChannel.setName("General chat");
            generalChannel.setDescription("General text chat");
            generalChannel.setSpaceId(newSpace.getId());

            spaceChannelRepository.save(generalChannel);
            System.out.println("Created default 'General' channel");
        }
    }
}