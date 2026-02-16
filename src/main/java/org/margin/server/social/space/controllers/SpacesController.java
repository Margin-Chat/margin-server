package org.margin.server.social.space.controllers;

import org.margin.server.social.space.models.dtos.CreateSpaceDTO;
import org.margin.server.social.space.models.dtos.SpaceDTO;
import org.margin.server.social.margin.MarginService;
import org.springframework.web.bind.annotation.*;
import org.margin.server.social.space.services.SpacesService;

import java.util.List;

@RestController
@RequestMapping("/spaces")
public class SpacesController {
    private final SpacesService spacesService;

    public SpacesController(SpacesService spacesService) {
        this.spacesService = spacesService;
    }

    @GetMapping("get_all_spaces")
    public List<SpaceDTO> getAllSpaces() {
        return spacesService.getSpaces();
    }

    @PostMapping("create_space")
    public void createSpace(@RequestBody CreateSpaceDTO dto) {
        spacesService.createNewSpace(dto);
    }
}
