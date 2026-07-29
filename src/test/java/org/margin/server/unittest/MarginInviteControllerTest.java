package org.margin.server.unittest;

import org.junit.jupiter.api.BeforeEach;
import static org.margin.server.unittest.utils.UserTestUtils.principalOf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.users.api.UserLookup;
import org.margin.server.social.margin.controllers.MarginInviteController;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.entities.MarginInvite;
import org.margin.server.social.margin.models.dtos.MarginDTO;
import org.margin.server.social.margin.models.dtos.MarginInviteDTO;
import org.margin.server.social.margin.service.MarginInviteService;
import org.margin.server.social.margin.service.MarginMapper;
import org.margin.server.social.margin.service.MarginService;
import org.margin.server.social.margin.validations.MarginAuthorizationService;
import org.margin.server.users.models.User;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.margin.server.unittest.utils.UserTestUtils.*;
import static org.margin.server.unittest.utils.MarginTestUtils.*;
import static org.margin.server.unittest.utils.MarginInviteTestUtils.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class MarginInviteControllerTest {

    @Mock
    private MarginInviteService marginInviteService;
    @Mock
    private MarginAuthorizationService marginAuthorizationService;
    @Mock
    private MarginService marginService;
    @Mock
    private UserLookup userLookup;
    @Mock
    private MarginMapper marginMapper;

    @InjectMocks
    private MarginInviteController marginInviteController;

    private User adminUser;
    private User targetUser;
    private Margin margin;

    @BeforeEach
    void setUp() {
        adminUser = createUser(1L, "admin");
        targetUser = createUser(2L, "targetUser");
        margin = createMargin(1L, "Test Margin");
        lenient().when(userLookup.summaryOf(adminUser.getId()))
                .thenReturn(new org.margin.server.users.api.UserSummary(adminUser.getId(), adminUser.getDisplayName()));
        lenient().when(userLookup.summaryOf(targetUser.getId()))
                .thenReturn(new org.margin.server.users.api.UserSummary(targetUser.getId(), targetUser.getDisplayName()));
    }

    @Test
    void createLinkInvite_shouldReturnInvite() {
        MarginInvite invite = createLinkInvite(margin, adminUser);
        when(marginService.getById(1L)).thenReturn(margin);
        when(marginInviteService.createLinkInvite(margin, 10, adminUser.getId())).thenReturn(invite);

        ResponseEntity<?> response = marginInviteController.createLinkInvite(1L, 10, principalOf(adminUser));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        MarginInviteDTO body = (MarginInviteDTO) response.getBody();
        assertThat(body.inviteCode()).isEqualTo("abc-123");
        assertThat(body.marginName()).isEqualTo("Test Margin");
        assertThat(body.invitedByUser()).isEqualTo("admin");
        verify(marginAuthorizationService).requireMarginAdmin(adminUser.getId(), 1L);
    }

    @Test
    void createLinkInvite_withNullMaxUses_shouldSucceed() {
        MarginInvite invite = createLinkInvite(margin, adminUser);
        when(marginService.getById(1L)).thenReturn(margin);
        when(marginInviteService.createLinkInvite(margin, null, adminUser.getId())).thenReturn(invite);

        ResponseEntity<?> response = marginInviteController.createLinkInvite(1L, null, principalOf(adminUser));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void createLinkInvite_withZeroMaxUses_shouldThrow400() {
        assertThatThrownBy(() -> marginInviteController.createLinkInvite(1L, 0, principalOf(adminUser)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createLinkInvite_withNegativeMaxUses_shouldThrow400() {
        assertThatThrownBy(() -> marginInviteController.createLinkInvite(1L, -5, principalOf(adminUser)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createDirectInvite_shouldReturnInvite() {
        MarginInvite invite = createDirectInvite(margin, adminUser, targetUser);
        when(userLookup.idByEmail("targetuser")).thenReturn(Optional.of(targetUser.getId()));
        when(marginService.isUserMember(1L, targetUser.getId())).thenReturn(false);
        when(marginInviteService.hasPendingInviteForMargin(1L, targetUser.getId())).thenReturn(false);
        when(marginService.getById(1L)).thenReturn(margin);
        when(marginInviteService.createDirectInvite(margin, targetUser.getId(), adminUser.getId())).thenReturn(invite);

        ResponseEntity<?> response = marginInviteController.createDirectInvite(1L, "targetUser", principalOf(adminUser));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        MarginInviteDTO body = (MarginInviteDTO) response.getBody();
        assertThat(body.inviteCode()).isEqualTo("direct-123");
        assertThat(body.invitedUser()).isEqualTo("targetUser");
        assertThat(body.marginName()).isEqualTo("Test Margin");
        verify(marginAuthorizationService).requireMarginAdmin(adminUser.getId(), 1L);
    }

    @Test
    void createDirectInvite_withEmptyEmail_shouldThrow400() {
        assertThatThrownBy(() -> marginInviteController.createDirectInvite(1L, "", principalOf(adminUser)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createDirectInvite_whenUserAlreadyMember_shouldThrow409() {
        when(userLookup.idByEmail("targetuser")).thenReturn(Optional.of(targetUser.getId()));
        when(marginService.isUserMember(1L, targetUser.getId())).thenReturn(true);

        assertThatThrownBy(() -> marginInviteController.createDirectInvite(1L, "targetUser", principalOf(adminUser)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createDirectInvite_whenPendingInviteExists_shouldThrow409() {
        when(userLookup.idByEmail("targetuser")).thenReturn(Optional.of(targetUser.getId()));
        when(marginService.isUserMember(1L, targetUser.getId())).thenReturn(false);
        when(marginInviteService.hasPendingInviteForMargin(1L, targetUser.getId())).thenReturn(true);

        assertThatThrownBy(() -> marginInviteController.createDirectInvite(1L, "targetUser", principalOf(adminUser)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getInviteDetails_shouldReturnDetails() {
        MarginInvite invite = createLinkInvite(margin, adminUser);
        when(marginInviteService.findInviteDetails("abc-123")).thenReturn(Optional.of(invite));

        ResponseEntity<?> response = marginInviteController.getInviteDetails("abc-123");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        MarginInviteDTO body = (MarginInviteDTO) response.getBody();
        assertThat(body.inviteCode()).isEqualTo("abc-123");
        assertThat(body.marginName()).isEqualTo("Test Margin");
    }

    @Test
    void getInviteDetails_whenExpired_shouldThrow410() {
        MarginInvite invite = createLinkInvite(margin, adminUser);
        invite.setExpiresAt(Instant.now().minus(Duration.ofDays(1)));
        when(marginInviteService.findInviteDetails("abc-123")).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> marginInviteController.getInviteDetails("abc-123"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void acceptLinkInvite_shouldSucceed() {
        MarginInvite invite = createLinkInvite(margin, adminUser);
        when(marginInviteService.findInviteDetails("abc-123")).thenReturn(Optional.of(invite));
        when(marginService.isUserMember(margin.getId(), targetUser.getId())).thenReturn(false);
        when(marginInviteService.acceptLinkInvite(invite, targetUser.getId())).thenReturn(margin);
        when(marginMapper.marginToDto(margin)).thenReturn(null);

        ResponseEntity<?> response = marginInviteController.acceptLinkInvite("abc-123", principalOf(targetUser));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(marginInviteService).acceptLinkInvite(invite, targetUser.getId());
        verify(marginMapper).marginToDto(margin);
    }

    @Test
    void acceptLinkInvite_whenExpired_shouldThrow410() {
        MarginInvite invite = createLinkInvite(margin, adminUser);
        invite.setExpiresAt(Instant.now().minus(Duration.ofDays(1)));
        when(marginInviteService.findInviteDetails("abc-123")).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> marginInviteController.acceptLinkInvite("abc-123", principalOf(targetUser)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void acceptLinkInvite_whenAlreadyMember_shouldThrow409() {
        MarginInvite invite = createLinkInvite(margin, adminUser);
        when(marginInviteService.findInviteDetails("abc-123")).thenReturn(Optional.of(invite));
        when(marginService.isUserMember(margin.getId(), targetUser.getId())).thenReturn(true);

        assertThatThrownBy(() -> marginInviteController.acceptLinkInvite("abc-123", principalOf(targetUser)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void acceptDirectInvite_shouldReturnMargin() {
        MarginInvite invite = createDirectInvite(margin, adminUser, targetUser);

        when(marginInviteService.getById(1L)).thenReturn(invite);
        when(marginInviteService.acceptDirectInvite(invite, targetUser.getId())).thenReturn(margin);

        MarginDTO dto = new MarginDTO(
                margin.getId(),
                margin.getName(),
                margin.getDescription(),
                margin.getVisibility(),
                margin.getIconUrl(),
                List.of(),
                List.of()
        );

        when(marginService.getMarginAsDto(margin.getId())).thenReturn(dto);

        ResponseEntity<?> response = marginInviteController.acceptDirectInvite(1L, principalOf(targetUser));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(dto);

        verify(marginService).getMarginAsDto(margin.getId());
    }

    @Test
    void acceptDirectInvite_whenNotTargetUser_shouldThrow403() {
        MarginInvite invite = createDirectInvite(margin, adminUser, targetUser);
        when(marginInviteService.getById(1L)).thenReturn(invite);

        User otherUser = createUser(99L, "other");

        assertThatThrownBy(() -> marginInviteController.acceptDirectInvite(1L, principalOf(otherUser)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void acceptDirectInvite_whenExpired_shouldThrow410() {
        MarginInvite invite = createDirectInvite(margin, adminUser, targetUser);
        invite.setExpiresAt(Instant.now().minus(Duration.ofDays(1)));
        when(marginInviteService.getById(1L)).thenReturn(invite);

        assertThatThrownBy(() -> marginInviteController.acceptDirectInvite(1L, principalOf(targetUser)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void declineDirectInvite_shouldReturn200() {
        MarginInvite invite = createDirectInvite(margin, adminUser, targetUser);
        when(marginInviteService.getById(1L)).thenReturn(invite);

        ResponseEntity<?> response = marginInviteController.declineDirectInvite(1L, principalOf(targetUser));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(marginInviteService).declineDirectInvite(invite);
    }

    @Test
    void declineDirectInvite_whenNotTargetUser_shouldThrow403() {
        MarginInvite invite = createDirectInvite(margin, adminUser, targetUser);
        when(marginInviteService.getById(1L)).thenReturn(invite);

        User otherUser = createUser(99L, "other");

        assertThatThrownBy(() -> marginInviteController.declineDirectInvite(1L, principalOf(otherUser)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void declineDirectInvite_whenAlreadyResolved_shouldThrow409() {
        MarginInvite invite = createDirectInvite(margin, adminUser, targetUser);
        invite.setStatus(MarginInvite.InviteStatus.ACCEPTED);
        when(marginInviteService.getById(1L)).thenReturn(invite);

        assertThatThrownBy(() -> marginInviteController.declineDirectInvite(1L, principalOf(targetUser)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getPendingInvites_shouldReturnList() {
        MarginInvite invite = createDirectInvite(margin, adminUser, targetUser);
        when(marginInviteService.getPendingInvites(targetUser.getId())).thenReturn(List.of(invite));

        ResponseEntity<?> response = marginInviteController.getPendingInvites(principalOf(targetUser));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        List<MarginInviteDTO> body = (List<MarginInviteDTO>) response.getBody();
        assertThat(body).hasSize(1);
        assertThat(body.get(0).inviteCode()).isEqualTo("direct-123");
        assertThat(body.get(0).marginName()).isEqualTo("Test Margin");
    }

}