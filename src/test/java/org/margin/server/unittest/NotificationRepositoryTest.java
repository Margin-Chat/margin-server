package org.margin.server.unittest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.margin.server.notifications.Notification;
import org.margin.server.notifications.NotificationType;
import org.margin.server.notifications.repositories.NotificationRepository;
import org.margin.server.users.models.User;
import org.margin.server.users.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    private User recipient;
    private User sender;

    @BeforeEach
    void setUp() {
        recipient = userRepository.save(testUser("recipient@margin.com"));
        sender = userRepository.save(testUser("sender@margin.com"));
    }

    @Test
    void findByRecipient_IdOrderByCreatedAtDesc_returnsInOrder() {
        Notification older = savedNotification(recipient, sender, 1L, 10L, false);
        older.setCreatedAt(LocalDateTime.now().minusDays(1));
        notificationRepository.save(older);

        Notification newer = savedNotification(recipient, sender, 2L, 10L, false);
        notificationRepository.save(newer);

        List<Notification> result = notificationRepository.findByRecipient_IdOrderByCreatedAtDesc(recipient.getId());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getReferenceId()).isEqualTo(2L);
        assertThat(result.get(1).getReferenceId()).isEqualTo(1L);
    }

    @Test
    void findByRecipient_IdAndSeenFalse_returnsOnlyUnseen() {
        savedNotification(recipient, sender, 1L, 10L, false);
        savedNotification(recipient, sender, 2L, 10L, true);

        List<Notification> result = notificationRepository.findByRecipient_IdAndSeenFalse(recipient.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReferenceId()).isEqualTo(1L);
    }

    @Test
    void countByRecipient_IdAndMarginIdAndSeenFalse_countsCorrectly() {
        savedNotification(recipient, sender, 1L, 10L, false);
        savedNotification(recipient, sender, 2L, 10L, false);
        savedNotification(recipient, sender, 3L, 20L, false); // different margin
        savedNotification(recipient, sender, 4L, 10L, true);  // seen

        long count = notificationRepository.countByRecipient_IdAndMarginIdAndSeenFalse(recipient.getId(), 10L);

        assertThat(count).isEqualTo(2);
    }

    @Test
    void findByRecipient_IdAndSeenFalse_doesNotReturnOtherUsersNotifications() {
        User otherRecipient = userRepository.save(testUser("other@margin.com"));
        savedNotification(recipient, sender, 1L, 10L, false);
        savedNotification(otherRecipient, sender, 2L, 10L, false);

        List<Notification> result = notificationRepository.findByRecipient_IdAndSeenFalse(recipient.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRecipient().getId()).isEqualTo(recipient.getId());
    }

    private Notification savedNotification(User recipient, User sender, Long referenceId, Long marginId, boolean seen) {
        Notification n = new Notification();
        n.setRecipient(recipient);
        n.setSender(sender);
        n.setType(NotificationType.ANNOUNCEMENT);
        n.setReferenceId(referenceId);
        n.setMarginId(marginId);
        n.setSeen(seen);
        n.setCreatedAt(LocalDateTime.now());
        return notificationRepository.save(n);
    }

    private User testUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setUsername(email);
        user.setDisplayName(email);
        user.setPassword("password");
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }
}