package org.margin.server.social.controllers;

import org.margin.server.social.models.Visibility;
import org.margin.server.social.models.margin.CreateNewMarginRequest;
import org.margin.server.social.services.MarginService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/margin")
public class MarginController {

    private final MarginService marginService;

    public MarginController(MarginService marginService) {
        this.marginService = marginService;
    }

    @PostMapping("/create_new_margin")
    public ResponseEntity<String> createNewMargin(@RequestBody CreateNewMarginRequest request) {
        marginService.createMargin(
                request.marginName(),
                request.marginDescription(),
                Visibility.valueOf(request.visibility()));

        return ResponseEntity.ok().build();
    }
}
