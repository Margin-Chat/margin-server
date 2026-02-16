package org.margin.server.social.space.services;

import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.margin.MarginService;
import org.margin.server.social.margin.models.Margin;
import org.margin.server.social.space.models.dtos.CreateSpaceDTO;
import org.margin.server.social.space.models.dtos.SpaceDTO;
import org.springframework.stereotype.Service;
import org.margin.server.social.space.models.Space;
import org.margin.server.social.space.repositories.SpacesRepository;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
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

    @Transactional
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
        spacesRepository.save(newSpace);

        log.info("Created default 'General' space");

        return spacesRepository.findById(newSpace.getId()).orElseThrow();
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
                space.getMargin().getId()
        );
    }
}