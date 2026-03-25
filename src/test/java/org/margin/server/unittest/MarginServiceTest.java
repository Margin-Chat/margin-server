package org.margin.server.unittest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.notifications.services.NotificationService;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.repositories.MarginMemberRepository;
import org.margin.server.social.margin.repositories.MarginRepository;
import org.margin.server.social.margin.service.MarginMapper;
import org.margin.server.social.margin.service.MarginService;
import org.margin.server.social.models.Visibility;
import org.margin.server.social.space.services.SpacesService;
import org.margin.server.storage.StorageService;
import org.margin.server.users.models.User;
import org.margin.server.users.services.UserService;
import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarginServiceTest {
    public static final String TEST_MARGIN = "Test Margin";
    public static final String TEST_DESCRIPTION = "Test Description";
    public static final Visibility VISIBILITY = Visibility.PUBLIC;

    @Mock
    private MarginRepository marginRepository;
    @Mock
    private MarginMemberRepository marginMemberRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private SpacesService spacesService;
    @Mock
    private UserService userService;
    @Mock
    private MarginMapper marginMapper;
    @Mock
    private WebSocketDeliveryService webSocketDeliveryService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private MarginService marginService;

    private User testUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        return user;
    }

    private void stubMarginSave() {
        when(marginRepository.save(any(Margin.class))).thenAnswer(invocation -> {
            Margin m = invocation.getArgument(0);
            m.setId(1L);
            m.setMembers(new ArrayList<>());
            return m;
        });
        when(marginRepository.findById(1L)).thenAnswer(_ -> {
            Margin m = new Margin();
            m.setId(1L);
            m.setMembers(new ArrayList<>());
            return Optional.of(m);
        });
        when(userService.getById(1L)).thenReturn(testUser());
    }

    @Test
    void shouldCreateMargin() {
        stubMarginSave();

        marginService.createMargin(TEST_MARGIN, TEST_DESCRIPTION, VISIBILITY, null, testUser());

        ArgumentCaptor<Margin> marginCaptor = ArgumentCaptor.forClass(Margin.class);
        verify(marginRepository, times(1)).save(marginCaptor.capture());

        Margin capturedMargin = marginCaptor.getValue();
        assertThat(capturedMargin.getName()).isEqualTo(TEST_MARGIN);
        assertThat(capturedMargin.getDescription()).isEqualTo(TEST_DESCRIPTION);
        assertThat(capturedMargin.getVisibility()).isEqualTo(VISIBILITY);

        verify(storageService, never()).saveMarginIcon(any());
    }

    @Test
    void shouldCreateMarginWithProfilePicture() {
        stubMarginSave();

        MockMultipartFile profilePicture = new MockMultipartFile(
                "profilePicture",
                "test-image.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        String expectedUrl = "https://storage.example.com/margins/test-image.jpg";
        when(storageService.saveMarginIcon(profilePicture)).thenReturn(expectedUrl);

        marginService.createMargin(TEST_MARGIN, TEST_DESCRIPTION, VISIBILITY, profilePicture, testUser());

        verify(storageService, times(1)).saveMarginIcon(profilePicture);

        ArgumentCaptor<Margin> marginCaptor = ArgumentCaptor.forClass(Margin.class);
        verify(marginRepository, times(1)).save(marginCaptor.capture());

        Margin capturedMargin = marginCaptor.getValue();
        assertThat(capturedMargin.getName()).isEqualTo(TEST_MARGIN);
        assertThat(capturedMargin.getDescription()).isEqualTo(TEST_DESCRIPTION);
        assertThat(capturedMargin.getVisibility()).isEqualTo(VISIBILITY);
        assertThat(capturedMargin.getIconUrl()).isEqualTo(expectedUrl);
    }

    @Test
    void shouldNotSaveProfilePictureWhenFileIsEmpty() {
        stubMarginSave();

        MockMultipartFile emptyFile = new MockMultipartFile(
                "profilePicture",
                "test-image.jpg",
                "image/jpeg",
                new byte[0]
        );

        marginService.createMargin(TEST_MARGIN, TEST_DESCRIPTION, VISIBILITY, emptyFile, testUser());

        verify(storageService, never()).saveMarginIcon(any());

        ArgumentCaptor<Margin> marginCaptor = ArgumentCaptor.forClass(Margin.class);
        verify(marginRepository, times(1)).save(marginCaptor.capture());

        Margin capturedMargin = marginCaptor.getValue();
        assertThat(capturedMargin.getIconUrl()).isNull();
    }

    @Test
    void shouldHandleStorageServiceFailure() {
        MockMultipartFile profilePicture = new MockMultipartFile(
                "profilePicture",
                "test-image.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        when(storageService.saveMarginIcon(profilePicture))
                .thenThrow(new RuntimeException("Storage failed"));

        assertThrows(RuntimeException.class, () ->
                marginService.createMargin(TEST_MARGIN, TEST_DESCRIPTION, VISIBILITY, profilePicture, testUser())
        );

        verify(marginRepository, never()).save(any());
    }
}