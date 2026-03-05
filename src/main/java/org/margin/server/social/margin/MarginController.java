package org.margin.server.social.margin;

import org.margin.server.social.margin.models.MarginRole;
import org.margin.server.social.margin.models.dtos.*;
import org.margin.server.social.models.Visibility;
import org.margin.server.users.models.User;
import org.margin.server.users.services.UserService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/margins")
public class MarginController {

    private final MarginService marginService;
    private final UserService userService;

    public MarginController(MarginService marginService, UserService userService) {
        this.marginService = marginService;
        this.userService = userService;
    }

    @PostMapping(value = "/create_new_margin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> createNewMargin(
            @RequestPart("data") CreateNewMarginRequest request,
            @RequestPart(value = "marginIcon", required = false) MultipartFile marginIcon,
            @AuthenticationPrincipal User user) {

        marginService.createMargin(
                request.marginName(),
                request.marginDescription(),
                Visibility.valueOf(request.visibility()),
                marginIcon,
                user);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/get_margin/{marginId}")
    public MarginDTO getMargin(@PathVariable Long marginId) {
        return marginService.toDTO(marginService.getById(marginId));
    }

    @GetMapping("/get_margins")
    public Set<MarginDTO> getMargins(@AuthenticationPrincipal User user) {
        return marginService.getMarginsForUser(user);
    }

    @PostMapping("/update_margin")
    public ResponseEntity<MarginDTO> updateMargin(@RequestBody UpdateMarginDTO updateMarginDTO,
                                                  @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(marginService.toDTO(marginService.updateMargin(updateMarginDTO)));
    }

    @PostMapping("/delete_margin")
    public ResponseEntity<Void> deleteMargin(@RequestBody Long marginId, @AuthenticationPrincipal User user) {
        marginService.deleteMargin(marginId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/add_margin_member")
    public ResponseEntity<MarginMemberDTO> addMarginMember(
            @RequestBody AddMarginMemberRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(marginService.memberToDto(
                marginService.addUserToMargin(request.marginId(), request.userId(), request.role())
        ));
    }


    @PostMapping("/update_member_role")
    public ResponseEntity<MarginMemberDTO> updateMarginMemberRole(
            @RequestBody UpdateMemberRoleRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(marginService.updateMarginMemberRole(request.marginId(), request.member()));
    }

    @PostMapping("/remove_margin_member")
    public ResponseEntity<Void> removeMarginMember(@RequestBody Long marginId,
                                                   @AuthenticationPrincipal User user) {
        marginService.removeMarginMember(marginId, user.getId());
        return ResponseEntity.ok().build();
    }
}
