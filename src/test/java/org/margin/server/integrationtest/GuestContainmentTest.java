package org.margin.server.integrationtest;

import org.junit.jupiter.api.Test;
import org.margin.server.authentication.models.AuthResponse;
import org.margin.server.authentication.services.JwtService;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.AuthTestUtils;
import org.margin.server.integrationtest.utils.MarginTestUtils;
import org.margin.server.integrationtest.utils.UserTestUtils;
import org.margin.server.integrationtest.utils.WebSocketTestUtils;
import org.margin.server.presence.PresenceService;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.models.MarginRole;
import org.margin.server.social.margin.service.MarginService;
import org.margin.server.users.api.UserLookup;
import org.margin.server.users.controllers.UserController;
import org.margin.server.users.models.User;
import org.margin.server.users.models.UserAccountType;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.users.repositories.UserRepository;
import org.margin.server.users.services.GuestCleanupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.WebSocket;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The exit gate for guest identity. Shadow rows in the users table only stay safe because guests
 * are barred from every credential path and excluded from every place users are listed; each of
 * those is asserted here rather than left to inspection.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GuestContainmentTest extends MarginTestRunner {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserLookup userLookup;
    @Autowired
    private UserController userController;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private PresenceService presenceService;
    @Autowired
    private GuestCleanupService guestCleanupService;
    @Autowired
    private MarginService marginService;

    private User guest() {
        return UserTestUtils.createGuest("Wanderer", Instant.now().plus(1, ChronoUnit.DAYS));
    }

    @Test
    void guestRowsCarryAnUnresolvableEmailAndAreFlagged() {
        User guest = guest();

        assertEquals(UserAccountType.GUEST, guest.getAccountType());
        assertTrue(guest.isGuest());
        assertTrue(guest.getEmail().endsWith("@guests.margin.invalid"),
                "guest addresses must be in an RFC 2606 reserved domain that can never receive mail");
        assertNotNull(guest.getPassword(), "a real hash, so any matches() call fails closed");
        assertNotNull(guest.getEncryption(),
                "several read paths dereference getEncryption() without a null check");
        assertTrue(userLookup.isGuest(guest.getId()));
    }

    @Test
    void aGuestCannotLogIn() {
        User guest = guest();

        AuthResponse response = AuthTestUtils.login(guest.getEmail(), "anything");

        assertFalse(response.success());
        assertEquals("Invalid credentials", response.message());
    }

    @Test
    void aGuestShapedMarginJwtIsRejectedOnTheWebSocketPath() {
        User guest = guest();
        User member = UserTestUtils.createUser("alice", "alice@margin.chat");

        String guestToken = jwtService.generateToken(guest.getEmail(), guest.getId(), 0);
        String memberToken = jwtService.generateToken(member.getEmail(), member.getId(), 0);

        // Positive control: the same call must succeed for a real account, otherwise this test
        // would pass even if the WebSocket path rejected everything.
        assertTrue(jwtService.extractAndValidateJwtTokenFromWebSocket("/ws?token=" + memberToken).isPresent(),
                "a real account must still authenticate");
        assertTrue(jwtService.extractAndValidateJwtTokenFromWebSocket("/ws?token=" + guestToken).isEmpty(),
                "a hand-minted margin JWT must not authenticate a guest");
    }

    @Test
    void aGuestNeverAppearsInTheOnlineUserList() throws Exception {
        User guest = guest();
        User member = UserTestUtils.createUser("alice", "alice@margin.chat");
        User other = UserTestUtils.createUser("bob", "bob@margin.chat");

        // Real connections: the online set is the WebSocket connection map, so publishing a
        // presence event is not enough to appear here.
        WebSocket memberSocket = WebSocketTestUtils.connect(member);
        WebSocket otherSocket = WebSocketTestUtils.connect(other);

        try {
            List<Long> visible = userController.getAllOnlineUsersOnServer(UserTestUtils.principalOf(member))
                    .stream().map(UserDTO::id).toList();

            assertTrue(visible.contains(other.getId()), "real online users must still be listed");
            assertFalse(visible.contains(guest.getId()), "guests must not be listed to other users");
        } finally {
            WebSocketTestUtils.close(memberSocket, otherSocket);
        }
    }

    @Test
    void aGuestCannotOpenAWebSocketAndSoNeverEntersThePresenceMap() throws Exception {
        User guest = guest();

        WebSocketTestUtils.Rejection rejection =
                WebSocketTestUtils.connectExpectingRejection(WebSocketTestUtils.validTokenFor(guest));

        assertNotNull(rejection, "a guest must not be able to open the app WebSocket");
        assertFalse(presenceService.isUserOnline(guest.getId()));
    }

    @Test
    void aGuestCannotBeFetchedByIdOrHavePublicKeysRead() {
        User guest = guest();

        assertThrows(ResponseStatusException.class, () -> userController.getUser(guest.getId()));
        assertThrows(ResponseStatusException.class, () -> userController.getPublicKey(guest.getId()));
    }

    @Test
    void aGuestCannotBeAddedToAMargin() {
        User guest = guest();
        User owner = UserTestUtils.createUser("olivia", "olivia@margin.chat");
        Margin margin = MarginTestUtils.createMargin("Acme", owner);

        assertThrows(ResponseStatusException.class,
                () -> marginService.addUserToMargin(margin.getId(), guest.getId(),
                        MarginRole.MEMBER, owner.getId(), false),
                "membership is the invariant that user search and seat counts depend on");
    }

    @Test
    void theCleanupJobRemovesGuestsPastTheirExpiry() {
        User expired = UserTestUtils.createGuest("Gone", Instant.now().minus(1, ChronoUnit.HOURS));
        User active = UserTestUtils.createGuest("Here", Instant.now().plus(1, ChronoUnit.DAYS));

        int removed = guestCleanupService.removeExpiredGuests(Instant.now());

        assertTrue(removed >= 1);
        assertTrue(userRepository.findById(expired.getId()).isEmpty());
        assertTrue(userRepository.findById(active.getId()).isPresent(),
                "an unexpired guest is still in a meeting");
    }

    @Test
    void theCleanupJobLeavesRealAccountsAlone() {
        User member = UserTestUtils.createUser("alice", "alice@margin.chat");

        guestCleanupService.removeExpiredGuests(Instant.now().plus(365, ChronoUnit.DAYS));

        assertTrue(userRepository.findById(member.getId()).isPresent());
    }

    @Test
    void promotingAGuestKeepsTheSameUserIdSoHistorySurvives() {
        User guest = guest();

        UserTestUtils.accountCommands().promoteGuest(
                guest.getId(), "claimed@margin.chat", "hashed", null, null, null, null);

        User promoted = userRepository.findById(guest.getId()).orElseThrow();
        assertEquals(UserAccountType.FULL, promoted.getAccountType());
        assertEquals("claimed@margin.chat", promoted.getEmail());
        assertNull(promoted.getGuestExpiresAt());
        assertFalse(userLookup.isGuest(guest.getId()));
    }

    @Test
    void aPromotedGuestIsNoLongerReapable() {
        User guest = UserTestUtils.createGuest("Gone", Instant.now().minus(1, ChronoUnit.HOURS));
        UserTestUtils.accountCommands().promoteGuest(
                guest.getId(), "claimed@margin.chat", "hashed", null, null, null, null);

        guestCleanupService.removeExpiredGuests(Instant.now());

        assertTrue(userRepository.findById(guest.getId()).isPresent(),
                "claiming an account must survive the reaper that would have removed the guest");
    }
}
