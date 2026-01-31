package org.margin.server.social.controllers;

import org.margin.server.social.models.space.dtos.CreateSpaceDTO;
import org.margin.server.social.models.space.dtos.SpaceDTO;
import org.margin.server.social.models.Visibility;
import org.margin.server.social.services.MarginService;
import org.springframework.web.bind.annotation.*;
import org.margin.server.social.services.SpacesService;

import java.util.List;

@RestController
@RequestMapping("/spaces")
public class SpacesController {
    private final SpacesService spacesService;
    private final MarginService marginService;

    public SpacesController(SpacesService spacesService, MarginService marginService) {
        this.spacesService = spacesService;
        this.marginService = marginService;
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
