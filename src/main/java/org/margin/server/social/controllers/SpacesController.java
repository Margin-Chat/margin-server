package org.margin.server.social.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
}
