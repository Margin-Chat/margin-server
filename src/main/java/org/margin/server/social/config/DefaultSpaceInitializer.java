package org.margin.server.social.config;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.models.channel.Channel;
import org.margin.server.social.models.margin.Margin;
import org.margin.server.social.models.space.Space;
import org.margin.server.social.models.space.dtos.CreateSpaceDTO;
import org.margin.server.social.models.space.dtos.SpaceDTO;
import org.margin.server.social.models.space.SpaceMember;
import org.margin.server.social.models.space.enums.SpaceMemberRole;
import org.margin.server.social.models.Visibility;
import org.margin.server.social.repositories.ChannelRepository;
import org.margin.server.social.repositories.MarginRepository;
import org.margin.server.social.repositories.SpaceMemberRepository;
import org.margin.server.social.repositories.SpacesRepository;
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

    private final SpacesRepository spacesRepository;
    private final ChannelRepository channelRepository;
    private final UserRepository userRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final SpacesService spacesService;
    private final MarginRepository marginRepository;

    public DefaultSpaceInitializer(SpacesRepository spacesRepository,
                                   ChannelRepository channelRepository,
                                   UserRepository userRepository,
                                   SpaceMemberRepository spaceMemberRepository,
                                   SpacesService spacesService, MarginRepository marginRepository) {
        this.spacesRepository = spacesRepository;
        this.channelRepository = channelRepository;
        this.userRepository = userRepository;
        this.spaceMemberRepository = spaceMemberRepository;
        this.spacesService = spacesService;
        this.marginRepository = marginRepository;
    }

    @Override
    public void run(String... args) {
        if (!spacesRepository.existsById(1L)) {

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

            log.info("Created default 'General' space");

            List<User> allUsers = userRepository.findAll();

            List<SpaceMember> members = new ArrayList<>();
            for (User user : allUsers) {
                SpaceMember spaceMember = new SpaceMember();
                spaceMember.setSpace(newSpace);
                spaceMember.setUser(user);
                spaceMember.setRole(SpaceMemberRole.MEMBER);
                spaceMember.setJoinedAt(LocalDateTime.now());

                members.add(spaceMember);
            }
            spaceMemberRepository.saveAll(members);

            Channel generalChannel = new Channel();
            generalChannel.setName("General chat");
            generalChannel.setDescription("General text chat");
            generalChannel.setSpace(newSpace);

            channelRepository.save(generalChannel);
            System.out.println("Created default 'General' channel");
        }
    }
}