package org.margin.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.models.Visibility;
import org.margin.server.social.models.margin.Margin;
import org.margin.server.social.repositories.MarginRepository;
import org.margin.server.social.services.MarginService;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarginServiceTest {

    @Mock
    private MarginRepository marginRepository;

    @InjectMocks
    private MarginService marginService;

    @Test
    void shouldCreateMargin() {
        String name = "Test Margin";
        String description = "Test Description";
        Visibility visibility = Visibility.PUBLIC;

        marginService.createMargin(name, description, visibility);

        ArgumentCaptor<Margin> marginCaptor = ArgumentCaptor.forClass(Margin.class);
        verify(marginRepository, times(1)).save(marginCaptor.capture());

        Margin capturedMargin = marginCaptor.getValue();
        assertThat(capturedMargin.getName()).isEqualTo(name);
        assertThat(capturedMargin.getDescription()).isEqualTo(description);
        assertThat(capturedMargin.getVisibility()).isEqualTo(visibility);
    }
}