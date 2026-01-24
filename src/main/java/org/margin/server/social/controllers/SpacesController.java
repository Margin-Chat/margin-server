package org.margin.server.social.controllers;

import org.margin.server.social.models.space.SpaceDTO;
import org.margin.server.social.models.space.enums.SpaceVisibility;
import org.springframework.web.bind.annotation.*;
import org.margin.server.social.models.space.Space;
import org.margin.server.social.services.SpacesService;

import java.util.List;

@RestController
@RequestMapping("/spaces")
public class SpacesController {
    private final SpacesService spacesService;

    public SpacesController(SpacesService spacesService) {
        this.spacesService = spacesService;
    }

    @GetMapping("get_all_spaces")
    public List<Space> getAllSpaces() {
        return spacesService.getSpaces();
    }

    @PostMapping("create_space")
    public void createSpace(@RequestBody String spaceName) {
        SpaceDTO generalSpace = new SpaceDTO(
                spaceName,
                "Default general space for all users",
                SpaceVisibility.PUBLIC);
        spacesService.createNewSpace(generalSpace);
    }
}
