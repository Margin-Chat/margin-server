package org.margin.server.unittest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.margin.controllers.MarginInviteController;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.entities.MarginInvite;
import org.margin.server.social.margin.models.dtos.MarginInviteDTO;
import org.margin.server.social.margin.service.MarginInviteService;
import org.margin.server.social.margin.service.MarginMapper;
import org.margin.server.social.margin.service.MarginService;
import org.margin.server.social.margin.validations.MarginAuthorizationService;
import org.margin.server.users.models.User;
import org.margin.server.users.services.UserService;
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
import static org.mockito.Mockito.when;

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
    private UserService userService;
    @Mock
    private MarginMapper marginMapper;

    @InjectMocks
    private MarginInviteController marginInviteController;

    private User adminUser;
    private User targetUser;
    private Margin margin;

    @BeforeEach
    void setUp() {
        adminUser = testUser(1L, "admin");
        targetUser = testUser(2L, "targetUser");
        margin = testMargin();
    }

    @Test
    void createLinkInvite_shouldReturnInvite() {
        MarginInvite invite = testLinkInvite(margin, adminUser);
        when(marginService.getById(1L)).thenReturn(margin);
        when(marginInviteService.createLinkInvite(margin, 10, adminUser)).thenReturn(invite);

        ResponseEntity<?> response = marginInviteController.createLinkInvite(1L, 10, adminUser);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        MarginInviteDTO body = (MarginInviteDTO) response.getBody();
        assertThat(body.inviteCode()).isEqualTo("abc-123");
        assertThat(body.marginName()).isEqualTo("Test Margin");
        assertThat(body.invitedByUser()).isEqualTo("admin");
        verify(marginAuthorizationService).requireMarginAdmin(adminUser.getId(), 1L);
    }

    @Test
    void createLinkInvite_withNullMaxUses_shouldSucceed() {
        MarginInvite invite = testLinkInvite(margin, adminUser);
        when(marginService.getById(1L)).thenReturn(margin);
        when(marginInviteService.createLinkInvite(margin, null, adminUser)).thenReturn(invite);

        ResponseEntity<?> response = marginInviteController.createLinkInvite(1L, null, adminUser);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void createLinkInvite_withZeroMaxUses_shouldThrow400() {
        assertThatThrownBy(() -> marginInviteController.createLinkInvite(1L, 0, adminUser))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createLinkInvite_withNegativeMaxUses_shouldThrow400() {
        assertThatThrownBy(() -> marginInviteController.createLinkInvite(1L, -5, adminUser))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createDirectInvite_shouldReturnInvite() {
        MarginInvite invite = testDirectInvite(margin, adminUser, targetUser);
        when(userService.getByHandle("targetUser")).thenReturn(targetUser);
        when(marginService.isUserMember(1L, targetUser)).thenReturn(false);
        when(marginInviteService.hasPendingInviteForMargin(1L, targetUser.getId())).thenReturn(false);
        when(marginService.getById(1L)).thenReturn(margin);
        when(marginInviteService.createDirectInvite(margin, targetUser, adminUser)).thenReturn(invite);

        ResponseEntity<?> response = marginInviteController.createDirectInvite(1L, "targetUser", adminUser);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        MarginInviteDTO body = (MarginInviteDTO) response.getBody();
        assertThat(body.inviteCode()).isEqualTo("direct-123");
        assertThat(body.invitedUser()).isEqualTo("targetUser");
        assertThat(body.marginName()).isEqualTo("Test Margin");
        verify(marginAuthorizationService).requireMarginAdmin(adminUser.getId(), 1L);
    }

    @Test
    void createDirectInvite_withEmptyHandle_shouldThrow400() {
        assertThatThrownBy(() -> marginInviteController.createDirectInvite(1L, "", adminUser))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createDirectInvite_whenUserAlreadyMember_shouldThrow409() {
        when(userService.getByHandle("targetUser")).thenReturn(targetUser);
        when(marginService.isUserMember(1L, targetUser)).thenReturn(true);

        assertThatThrownBy(() -> marginInviteController.createDirectInvite(1L, "targetUser", adminUser))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createDirectInvite_whenPendingInviteExists_shouldThrow409() {
        when(userService.getByHandle("targetUser")).thenReturn(targetUser);
        when(marginService.isUserMember(1L, targetUser)).thenReturn(false);
        when(marginInviteService.hasPendingInviteForMargin(1L, targetUser.getId())).thenReturn(true);

        assertThatThrownBy(() -> marginInviteController.createDirectInvite(1L, "targetUser", adminUser))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getInviteDetails_shouldReturnDetails() {
        MarginInvite invite = testLinkInvite(margin, adminUser);
        when(marginInviteService.findInviteDetails("abc-123")).thenReturn(Optional.of(invite));

        ResponseEntity<?> response = marginInviteController.getInviteDetails("abc-123");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        MarginInviteDTO body = (MarginInviteDTO) response.getBody();
        assertThat(body.inviteCode()).isEqualTo("abc-123");
        assertThat(body.marginName()).isEqualTo("Test Margin");
    }

    @Test
    void getInviteDetails_whenExpired_shouldThrow410() {
        MarginInvite invite = testLinkInvite(margin, adminUser);
        invite.setExpiresAt(Instant.now().minus(Duration.ofDays(1)));
        when(marginInviteService.findInviteDetails("abc-123")).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> marginInviteController.getInviteDetails("abc-123"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void acceptLinkInvite_shouldSucceed() {
        MarginInvite invite = testLinkInvite(margin, adminUser);
        when(marginInviteService.findInviteDetails("abc-123")).thenReturn(Optional.of(invite));
        when(marginService.isUserMember(margin.getId(), targetUser)).thenReturn(false);
        when(marginInviteService.acceptLinkInvite(invite, targetUser)).thenReturn(margin);
        when(marginMapper.marginToDto(margin)).thenReturn(null);

        ResponseEntity<?> response = marginInviteController.acceptLinkInvite("abc-123", targetUser);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(marginInviteService).acceptLinkInvite(invite, targetUser);
        verify(marginMapper).marginToDto(margin);
    }

    @Test
    void acceptLinkInvite_whenExpired_shouldThrow410() {
        MarginInvite invite = testLinkInvite(margin, adminUser);
        invite.setExpiresAt(Instant.now().minus(Duration.ofDays(1)));
        when(marginInviteService.findInviteDetails("abc-123")).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> marginInviteController.acceptLinkInvite("abc-123", targetUser))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void acceptLinkInvite_whenAlreadyMember_shouldThrow409() {
        MarginInvite invite = testLinkInvite(margin, adminUser);
        when(marginInviteService.findInviteDetails("abc-123")).thenReturn(Optional.of(invite));
        when(marginService.isUserMember(margin.getId(), targetUser)).thenReturn(true);

        assertThatThrownBy(() -> marginInviteController.acceptLinkInvite("abc-123", targetUser))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void acceptDirectInvite_shouldReturnMargin() {
        MarginInvite invite = testDirectInvite(margin, adminUser, targetUser);
        when(marginInviteService.getById(1L)).thenReturn(invite);
        when(marginInviteService.acceptDirectInvite(invite, targetUser)).thenReturn(margin);
        when(marginMapper.marginToDto(margin)).thenReturn(null);

        ResponseEntity<?> response = marginInviteController.acceptDirectInvite(1L, targetUser);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(marginMapper).marginToDto(margin);
    }

    @Test
    void acceptDirectInvite_whenNotTargetUser_shouldThrow403() {
        MarginInvite invite = testDirectInvite(margin, adminUser, targetUser);
        when(marginInviteService.getById(1L)).thenReturn(invite);

        User otherUser = testUser(99L, "other");

        assertThatThrownBy(() -> marginInviteController.acceptDirectInvite(1L, otherUser))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void acceptDirectInvite_whenExpired_shouldThrow410() {
        MarginInvite invite = testDirectInvite(margin, adminUser, targetUser);
        invite.setExpiresAt(Instant.now().minus(Duration.ofDays(1)));
        when(marginInviteService.getById(1L)).thenReturn(invite);

        assertThatThrownBy(() -> marginInviteController.acceptDirectInvite(1L, targetUser))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void declineDirectInvite_shouldReturn200() {
        MarginInvite invite = testDirectInvite(margin, adminUser, targetUser);
        when(marginInviteService.getById(1L)).thenReturn(invite);

        ResponseEntity<?> response = marginInviteController.declineDirectInvite(1L, targetUser);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(marginInviteService).declineDirectInvite(invite);
    }

    @Test
    void declineDirectInvite_whenNotTargetUser_shouldThrow403() {
        MarginInvite invite = testDirectInvite(margin, adminUser, targetUser);
        when(marginInviteService.getById(1L)).thenReturn(invite);

        User otherUser = testUser(99L, "other");

        assertThatThrownBy(() -> marginInviteController.declineDirectInvite(1L, otherUser))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void declineDirectInvite_whenAlreadyResolved_shouldThrow409() {
        MarginInvite invite = testDirectInvite(margin, adminUser, targetUser);
        invite.setStatus(MarginInvite.InviteStatus.ACCEPTED);
        when(marginInviteService.getById(1L)).thenReturn(invite);

        assertThatThrownBy(() -> marginInviteController.declineDirectInvite(1L, targetUser))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getPendingInvites_shouldReturnList() {
        MarginInvite invite = testDirectInvite(margin, adminUser, targetUser);
        when(marginInviteService.getPendingInvites(targetUser)).thenReturn(List.of(invite));

        ResponseEntity<?> response = marginInviteController.getPendingInvites(targetUser);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        List<MarginInviteDTO> body = (List<MarginInviteDTO>) response.getBody();
        assertThat(body).hasSize(1);
        assertThat(body.get(0).inviteCode()).isEqualTo("direct-123");
        assertThat(body.get(0).marginName()).isEqualTo("Test Margin");
    }

    private User testUser(Long id, String handle) {
        User user = new User();
        user.setId(id);
        user.setHandle(handle);
        return user;
    }

    private Margin testMargin() {
        Margin newMargin = new Margin();
        newMargin.setId(1L);
        newMargin.setName("Test Margin");
        return newMargin;
    }

    private MarginInvite testLinkInvite(Margin margin, User invitedBy) {
        MarginInvite invite = new MarginInvite();
        invite.setId(1L);
        invite.setMargin(margin);
        invite.setInvitedBy(invitedBy);
        invite.setType(MarginInvite.InviteType.LINK);
        invite.setStatus(MarginInvite.InviteStatus.PENDING);
        invite.setExpiresAt(Instant.now().plus(Duration.ofDays(7)));
        invite.setInviteCode("abc-123");
        return invite;
    }

    private MarginInvite testDirectInvite(Margin margin, User invitedBy, User invitedUser) {
        MarginInvite invite = new MarginInvite();
        invite.setId(1L);
        invite.setMargin(margin);
        invite.setInvitedBy(invitedBy);
        invite.setInvitedUser(invitedUser);
        invite.setType(MarginInvite.InviteType.DIRECT);
        invite.setStatus(MarginInvite.InviteStatus.PENDING);
        invite.setMaxUses(1);
        invite.setExpiresAt(Instant.now().plus(Duration.ofDays(7)));
        invite.setInviteCode("direct-123");
        return invite;
    }
}