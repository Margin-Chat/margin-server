package org.margin.server.social.services;

import org.margin.server.social.models.space.SpaceDTO;
import org.springframework.stereotype.Service;
import org.margin.server.social.models.space.Space;
import org.margin.server.social.repositories.SpaceRepository;

import java.util.List;
import java.util.Optional;

@Service
public class SpacesService {
    private final SpaceRepository spaceRepository;

    public SpacesService(SpaceRepository spaceRepository) {
        this.spaceRepository = spaceRepository;
    }

    public List<Space> getSpaces() {
        return spaceRepository.getSpaces();
    }

    public Space createNewSpace(SpaceDTO space) {
        Optional<Space> spaceByName = spaceRepository.getSpaceByName(space.name());

        if (spaceByName.isPresent()) {
            return null;
        }

        Space newSpace = new Space();
        newSpace.setName(space.name());
        newSpace.setDescription(space.description());
        newSpace.setVisibility(space.visibility());

        return spaceRepository.save(newSpace);
    }
}