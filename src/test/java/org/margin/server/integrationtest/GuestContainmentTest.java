package org.margin.server.integrationtest;

import org.junit.jupiter.api.Test;
import org.margin.server.authentication.filters.JwtAuthenticationFilter;
import org.margin.server.authentication.models.AuthResponse;
import org.margin.server.authentication.services.AuthenticationService;
import org.margin.server.authentication.services.JwtService;
import org.margin.server.email.EmailService;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.UserTestUtils;
import org.margin.server.notifications.repositories.NotificationRepository;
import org.margin.server.notifications.services.NotificationService;
import org.margin.server.shared.notifications.NotificationType;
import org.margin.server.users.api.UserAccountCommands;
import org.margin.server.users.api.UserLookup;
import org.margin.server.users.controllers.UserController;
import org.margin.server.users.models.User;
import org.margin.server.users.models.UserAccountType;
import org.margin.server.users.repositories.UserRepository;
import org.margin.server.users.services.GuestCleanupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The gate for guest containment. Guests are real rows in the users table so that every
 * Long-keyed path keeps working, which means the blast radius has to be closed deliberately.
 * Each test here corresponds to a way a guest could leak into the product.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GuestContainmentTest extends MarginTestRunner {

    @Autowired
    private UserAccountCommands userAccountCommands;
    @Autowired
    private UserLookup userLookup;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserController userController;
    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @Autowired
    private GuestCleanupService guestCleanupService;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private EmailService emailService;

    private User createGuest(Instant expiresAt) {
        Long id = userAccountCommands.createGuest("Guest Gary", expiresAt);
        return userRepository.findById(id).orElseThrow();
    }

    private User createGuest() {
        return createGuest(Instant.now().plus(24, ChronoUnit.HOURS));
    }

    @Test
    void createGuest_marksAccountTypeAndUsesAnUnresolvableEmail() {
        User guest = createGuest();

        assertEquals(UserAccountType.GUEST, guest.getAccountType());
        assertTrue(guest.isGuest());
        assertTrue(guest.getEmail().endsWith("@guests.margin.invalid"),
                "guest emails must be unresolvable, got " + guest.getEmail());
        assertNotNull(guest.getGuestExpiresAt());
        assertNotNull(guest.getEncryption(), "several read paths dereference encryption unguarded");
        assertTrue(userLookup.isGuest(guest.getId()));
    }

    @Test
    void login_asGuest_failsAsInvalidCredentialsRatherThanErroring() {
        User guest = createGuest();

        AuthResponse response = assertDoesNotThrow(
                () -> authenticationService.authenticateUser(guest.getEmail(), "anything"),
                "must not fall through to the activation check, which throws on a missing key");

        assertFalse(response.success());
        assertEquals("Invalid credentials", response.message());
    }

    @Test
    void httpFilter_rejectsAValidlySignedMarginTokenMintedForAGuest() throws Exception {
        User guest = createGuest();
        String token = jwtService.generateToken(guest.getEmail(), guest.getId(), 0);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        SecurityContextHolder.clearContext();
        try {
            jwtAuthenticationFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

            assertNull(SecurityContextHolder.getContext().getAuthentication(),
                    "a guest-shaped JWT must not authenticate; UserSecurityService.get() returns a "
                            + "transient tokenVersion=0 default, so nothing else would stop it");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void webSocketHandshake_rejectsAGuestToken() {
        User guest = createGuest();
        String token = jwtService.generateToken(guest.getEmail(), guest.getId(), 0);

        Optional<?> principal =
                jwtService.extractAndValidateJwtTokenFromWebSocket("/ws?token=" + token);

        assertTrue(principal.isEmpty(),
                "guests must never reach ConnectionManager or PresenceService");
    }

    @Test
    void getUserById_returnsNotFoundForAGuest() {
        User guest = createGuest();

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> userController.getUser(guest.getId()));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void getPublicKey_returnsNotFoundForAGuest() {
        User guest = createGuest();

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> userController.getPublicKey(guest.getId()));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void lookupByEmail_returnsNotFoundForAGuest() {
        User requester = UserTestUtils.createUser("alice", "alice@margin.chat");
        User guest = createGuest();

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> UserTestUtils.lookupByEmail(requester, guest.getEmail()));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void onlineUsers_neverIncludeGuests() {
        User requester = UserTestUtils.createUser("alice", "alice@margin.chat");
        createGuest();

        assertTrue(userController.getAllOnlineUsersOnServer(UserTestUtils.principalOf(requester))
                        .stream().noneMatch(dto -> dto.displayName().equals("Guest Gary")));
    }

    @Test
    void notifications_areNotCreatedForGuestRecipients() {
        User sender = UserTestUtils.createUser("alice", "alice@margin.chat");
        User guest = createGuest();

        notificationService.createForUsers(List.of(guest.getId()), sender.getId(),
                NotificationType.ANNOUNCEMENT, 1L, null);

        assertTrue(notificationRepository.findAll().stream()
                        .noneMatch(n -> n.getRecipientId().equals(guest.getId())),
                "an undeliverable notification just pollutes the table");
    }

    @Test
    void email_refusesToSendToAGuestAddress() {
        User guest = createGuest();

        assertDoesNotThrow(() -> emailService.sendEmail(guest.getEmail(), "subject", "<p>body</p>"));
    }

    @Test
    void cleanup_removesExpiredGuestsAndLeavesLiveOnesAlone() {
        User expired = createGuest(Instant.now().minus(1, ChronoUnit.HOURS));
        User live = createGuest(Instant.now().plus(1, ChronoUnit.HOURS));

        int removed = guestCleanupService.removeExpiredGuests(Instant.now());

        assertEquals(1, removed);
        assertTrue(userRepository.findById(expired.getId()).isEmpty());
        assertTrue(userRepository.findById(live.getId()).isPresent());
    }

    @Test
    void cleanup_neverTouchesFullAccounts() {
        User real = UserTestUtils.createUser("alice", "alice@margin.chat");

        guestCleanupService.removeExpiredGuests(Instant.now().plus(365, ChronoUnit.DAYS));

        assertTrue(userRepository.findById(real.getId()).isPresent());
    }

    @Test
    void promoteGuest_convertsInPlaceKeepingTheSameUserId() {
        User guest = createGuest();
        Long id = guest.getId();

        userAccountCommands.promoteGuest(id, "Gary@Margin.chat", "hashed", "pub", "priv", "salt", "iv");

        User promoted = userRepository.findById(id).orElseThrow();
        assertEquals(UserAccountType.FULL, promoted.getAccountType());
        assertEquals("gary@margin.chat", promoted.getEmail());
        assertNull(promoted.getGuestExpiresAt());
        assertFalse(userLookup.isGuest(id));
    }

    @Test
    void promotedGuest_isNoLongerReapedByCleanup() {
        User guest = createGuest(Instant.now().minus(1, ChronoUnit.HOURS));

        userAccountCommands.promoteGuest(guest.getId(), "gary@margin.chat", "hashed", "p", "p", "s", "i");
        guestCleanupService.removeExpiredGuests(Instant.now());

        assertTrue(userRepository.findById(guest.getId()).isPresent());
    }
}
