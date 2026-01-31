package org.margin.server.social.services;

import jakarta.validation.constraints.NotNull;
import org.margin.server.social.models.margin.Margin;
import org.margin.server.social.models.space.dtos.CreateSpaceDTO;
import org.margin.server.social.models.space.dtos.SpaceDTO;
import org.springframework.stereotype.Service;
import org.margin.server.social.models.space.Space;
import org.margin.server.social.repositories.SpacesRepository;

import java.util.List;
import java.util.Optional;

@Service
public class SpacesService {
    private final SpacesRepository spacesRepository;
    private final MarginService marginService;

    public SpacesService(SpacesRepository spacesRepository, MarginService marginService) {
        this.spacesRepository = spacesRepository;
        this.marginService = marginService;
    }

    public List<SpaceDTO> getSpaces() {
        return spacesRepository.getSpaces()
                .stream()
                .map(this::toDto)
                .toList();
    }

    public Space createNewSpace(CreateSpaceDTO dto) {
        Optional<Space> spaceByName = spacesRepository.getSpaceByName(dto.name());

        if (spaceByName.isPresent()) {
            return null;
        }

        Margin margin = marginService.getMargin(dto.marginId());

        Space newSpace = new Space();
        newSpace.setName(dto.name());
        newSpace.setDescription(dto.description());
        newSpace.setVisibility(dto.visibility());
        newSpace.setMargin(margin);

        return spacesRepository.save(newSpace);
    }

    public @NotNull Space getById(Long id) {
        return spacesRepository.findById(id).orElseThrow(() -> new RuntimeException("Space not found"));
    }

    private SpaceDTO toDto(Space space) {
        return new SpaceDTO(
                space.getId(),
                space.getName(),
                space.getDescription(),
                space.getVisibility(),
                space.getMargin().getId() // SAFE
        );
    }
}