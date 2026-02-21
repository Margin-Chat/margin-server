package org.margin.server.social.margin;

import org.margin.server.social.margin.models.Margin;
import org.margin.server.social.margin.models.dtos.MarginDTO;
import org.margin.server.social.models.Visibility;
import org.margin.server.social.margin.models.dtos.CreateNewMarginRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/margin")
public class MarginController {

    private final MarginService marginService;

    public MarginController(MarginService marginService) {
        this.marginService = marginService;
    }

    @PostMapping(value = "/create_new_margin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> createNewMargin(
            @RequestPart("data") CreateNewMarginRequest request,
            @RequestPart(value = "marginIcon", required = false) MultipartFile marginIcon) {

        marginService.createMargin(
                request.marginName(),
                request.marginDescription(),
                Visibility.valueOf(request.visibility()),
                marginIcon);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/get_margin/{marginId}")
    public MarginDTO getMargin(@PathVariable Long marginId) {
        return new MarginDTO(marginService.getMargin(marginId));
    }

}
