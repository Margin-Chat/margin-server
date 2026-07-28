package org.margin.server.unittest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.margin.events.UserInvitedToMarginEvent;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.entities.MarginInvite;
import org.margin.server.social.margin.models.MarginRole;
import org.margin.server.social.margin.repositories.MarginInviteRepository;
import org.margin.server.social.margin.repositories.MarginMemberRepository;
import org.margin.server.social.margin.service.MarginInviteService;
import org.margin.server.social.margin.service.MarginService;
import org.margin.server.social.space.services.SpacesService;
import org.margin.server.users.models.User;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.margin.server.unittest.utils.UserTestUtils.*;
import static org.margin.server.unittest.utils.MarginTestUtils.*;
import static org.margin.server.unittest.utils.MarginInviteTestUtils.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class MarginInviteServiceTest {

    @Mock
    private MarginInviteRepository marginInviteRepository;
    @Mock
    private MarginMemberRepository marginMemberRepository;
    @Mock
    private MarginService marginService;
    @Mock
    private SpacesService spacesService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MarginInviteService marginInviteService;

    private User admin;
    private User targetUser;
    private Margin margin;

    @BeforeEach
    void setUp() {
        admin = createUser(1L, "admin");
        targetUser = createUser(2L, "targetUser");
        margin = createMargin(1L, "Test Margin");
    }

    @Test
    void createLinkInvite_shouldCreateInviteWithCorrectFields() {
        when(marginInviteRepository.save(any(MarginInvite.class)))
                .thenAnswer(inv -> {
                    MarginInvite saved = inv.getArgument(0);
                    saved.setId(1L);
                    return saved;
                });

        MarginInvite result = marginInviteService.createLinkInvite(margin, 10, admin);

        assertThat(result.getMargin()).isEqualTo(margin);
        assertThat(result.getInvitedBy()).isEqualTo(admin);
        assertThat(result.getInvitedUser()).isNull();
        assertThat(result.getMaxUses()).isEqualTo(10);
        assertThat(result.getType()).isEqualTo(MarginInvite.InviteType.LINK);
        assertThat(result.getStatus()).isEqualTo(MarginInvite.InviteStatus.PENDING);
        assertThat(result.getInviteCode()).isNotNull();
        assertThat(result.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofDays(6)));
    }

    @Test
    void createLinkInvite_withNullMaxUses_shouldCreateUnlimitedInvite() {
        when(marginInviteRepository.save(any(MarginInvite.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        MarginInvite result = marginInviteService.createLinkInvite(margin, null, admin);

        assertThat(result.getMaxUses()).isNull();
    }

    @Test
    void createDirectInvite_shouldCreateInviteAndNotifyUser() {
        when(marginMemberRepository.existsByMarginIdAndUserId(margin.getId(), targetUser.getId()))
                .thenReturn(false);
        when(marginInviteRepository.existsPendingInvite(margin.getId(), targetUser.getId(), MarginInvite.InviteStatus.PENDING))
                .thenReturn(false);
        when(marginInviteRepository.save(any(MarginInvite.class)))
                .thenAnswer(inv -> {
                    MarginInvite saved = inv.getArgument(0);
                    saved.setId(1L);
                    return saved;
                });

        MarginInvite result = marginInviteService.createDirectInvite(margin, targetUser, admin);

        assertThat(result.getType()).isEqualTo(MarginInvite.InviteType.DIRECT);
        assertThat(result.getInvitedUser()).isEqualTo(targetUser);
        assertThat(result.getMaxUses()).isEqualTo(1);
        ArgumentCaptor<UserInvitedToMarginEvent> captor = ArgumentCaptor.forClass(UserInvitedToMarginEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getInvitedUser()).isEqualTo(targetUser);
        assertThat(captor.getValue().getInvitedBy()).isEqualTo(admin);
        assertThat(captor.getValue().getInviteCode()).isEqualTo(result.getInviteCode());
        assertThat(captor.getValue().getMarginId()).isEqualTo(margin.getId());
    }

    @Test
    void createDirectInvite_shouldThrowIfUserAlreadyMember() {
        when(marginMemberRepository.existsByMarginIdAndUserId(margin.getId(), targetUser.getId()))
                .thenReturn(true);

        assertThatThrownBy(() -> marginInviteService.createDirectInvite(margin, targetUser, admin))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already a member");
    }

    @Test
    void createDirectInvite_shouldThrowIfPendingInviteExists() {
        when(marginMemberRepository.existsByMarginIdAndUserId(margin.getId(), targetUser.getId()))
                .thenReturn(false);
        when(marginInviteRepository.existsPendingInvite(margin.getId(), targetUser.getId(), MarginInvite.InviteStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> marginInviteService.createDirectInvite(margin, targetUser, admin))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pending invite");
    }

    @Test
    void acceptLinkInvite_shouldAddUserToMarginAndDefaultSpaces() {
        MarginInvite invite = createLinkInvite(margin, admin);

        when(marginService.getById(margin.getId())).thenReturn(margin);

        marginInviteService.acceptLinkInvite(invite, targetUser);

        verify(marginService).addUserToMargin(
                eq(margin.getId()),
                eq(targetUser.getId()),
                eq(MarginRole.MEMBER),
                eq(admin),
                eq(false));
        verify(spacesService).addUsersToDefaultSpacesForMargin(
                eq(margin.getId()),
                eq(targetUser));

        assertThat(invite.getCurrentUses()).isEqualTo(1);
        verify(marginInviteRepository).save(invite);
    }

    @Test
    void acceptDirectInvite_shouldAddUserAndSetStatusAccepted() {
        MarginInvite invite = createDirectInvite(margin, admin, targetUser);

        when(marginService.getById(margin.getId())).thenReturn(margin);

        marginInviteService.acceptDirectInvite(invite, targetUser);

        verify(marginService).addUserToMargin(
                eq(margin.getId()),
                eq(targetUser.getId()),
                eq(MarginRole.MEMBER),
                eq(admin),
                eq(false));
        verify(spacesService).addUsersToDefaultSpacesForMargin(
                eq(margin.getId()),
                eq(targetUser));

        assertThat(invite.getStatus()).isEqualTo(MarginInvite.InviteStatus.ACCEPTED);
        verify(marginInviteRepository).save(invite);
    }

    @Test
    void declineDirectInvite_shouldSetStatusDeclined() {
        MarginInvite invite = createDirectInvite(margin, admin, targetUser);

        marginInviteService.declineDirectInvite(invite);

        assertThat(invite.getStatus()).isEqualTo(MarginInvite.InviteStatus.DECLINED);
        verify(marginInviteRepository).save(invite);
    }

    @Test
    void getPendingInvites_shouldReturnPendingInvitesForUser() {
        MarginInvite invite = createDirectInvite(margin, admin, targetUser);
        when(marginInviteRepository.findInvitesForUserByStatus(
                eq(targetUser.getId()),
                eq(MarginInvite.InviteStatus.PENDING),
                any(Instant.class)))
                .thenReturn(List.of(invite));

        List<MarginInvite> result = marginInviteService.getPendingInvites(targetUser);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getInvitedUser()).isEqualTo(targetUser);
    }

    @Test
    void hasPendingInviteForMargin_shouldReturnTrueWhenExists() {
        when(marginInviteRepository.existsPendingInvite(margin.getId(), targetUser.getId(), MarginInvite.InviteStatus.PENDING))
                .thenReturn(true);

        assertThat(marginInviteService.hasPendingInviteForMargin(margin.getId(), targetUser.getId())).isTrue();
    }

    @Test
    void hasPendingInviteForMargin_shouldReturnFalseWhenNotExists() {
        when(marginInviteRepository.existsPendingInvite(margin.getId(), targetUser.getId(), MarginInvite.InviteStatus.PENDING))
                .thenReturn(false);

        assertThat(marginInviteService.hasPendingInviteForMargin(margin.getId(), targetUser.getId())).isFalse();
    }

    @Test
    void getById_shouldReturnInviteWhenExists() {
        MarginInvite invite = createDirectInvite(margin, admin, targetUser);
        invite.setId(1L);
        when(marginInviteRepository.findById(1L)).thenReturn(Optional.of(invite));

        MarginInvite result = marginInviteService.getById(1L);

        assertThat(result).isEqualTo(invite);
    }

    @Test
    void getById_shouldThrowWhenNotFound() {
        when(marginInviteRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> marginInviteService.getById(999L))
                .isInstanceOf(RuntimeException.class);
    }

}