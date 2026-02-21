package org.margin.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.models.Visibility;
import org.margin.server.social.margin.models.Margin;
import org.margin.server.social.margin.MarginRepository;
import org.margin.server.social.margin.MarginService;
import org.margin.server.storage.StorageService;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

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
    private StorageService storageService;

    @InjectMocks
    private MarginService marginService;

    @Test
    void shouldCreateMargin() {
        marginService.createMargin(TEST_MARGIN, TEST_DESCRIPTION, VISIBILITY, null);

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
        MockMultipartFile profilePicture = new MockMultipartFile(
                "profilePicture",
                "test-image.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        String expectedUrl = "https://storage.example.com/margins/test-image.jpg";
        when(storageService.saveMarginIcon(profilePicture)).thenReturn(expectedUrl);

        marginService.createMargin(TEST_MARGIN, TEST_DESCRIPTION, VISIBILITY, profilePicture);

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
        MockMultipartFile emptyFile = new MockMultipartFile(
                "profilePicture",
                "test-image.jpg",
                "image/jpeg",
                new byte[0]
        );

        marginService.createMargin(TEST_MARGIN, TEST_DESCRIPTION, VISIBILITY, emptyFile);

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
                marginService.createMargin(TEST_MARGIN, TEST_DESCRIPTION, VISIBILITY, profilePicture)
        );

        verify(marginRepository, never()).save(any());
    }
}