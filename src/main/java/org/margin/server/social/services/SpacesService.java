package org.margin.server.social.services;

import org.springframework.stereotype.Service;
import org.margin.server.social.models.space.Space;
import org.margin.server.social.repositories.SpaceRepository;

import java.util.List;

@Service
public class SpacesService {
    private final SpaceRepository spaceRepository;

    public SpacesService(SpaceRepository spaceRepository) {
        this.spaceRepository = spaceRepository;
    }

    public List<Space> getSpaces() {
        return spaceRepository.getSpaces();
    }
}
