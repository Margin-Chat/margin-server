package org.margin.server.social.config;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.models.SpaceChannel;
import org.margin.server.social.models.space.Space;
import org.margin.server.social.models.space.enums.SpaceVisibility;
import org.margin.server.social.repositories.SpaceChannelRepository;
import org.margin.server.social.repositories.SpaceRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DefaultSpaceInitializer implements CommandLineRunner {

    private final SpaceRepository spaceRepository;
    private final SpaceChannelRepository spaceChannelRepository;

    public DefaultSpaceInitializer(SpaceRepository spaceRepository, SpaceChannelRepository spaceChannelRepository) {
        this.spaceRepository = spaceRepository;
        this.spaceChannelRepository = spaceChannelRepository;
    }

    @Override
    public void run(String... args) {
        if (!spaceRepository.existsById(1L)) {

            Space generalSpace = new Space();
            generalSpace.setId(1L);
            generalSpace.setName("General");
            generalSpace.setDescription("Default general space for all users");
            generalSpace.setVisibility(SpaceVisibility.PUBLIC);

            spaceRepository.save(generalSpace);
            log.info("Created default 'General' space");

            SpaceChannel generalChannel = new SpaceChannel();
            generalChannel.setId(1L);
            generalChannel.setName("General chat");
            generalChannel.setDescription("General text chat");
            generalChannel.setSpace(generalSpace);

            spaceChannelRepository.save(generalChannel);
            System.out.println("Created default 'General' channel");
        }
    }
}