package org.margin.server.unittest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.users.api.UserLookup;
import org.margin.server.notifications.services.NotificationService;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.entities.MarginMember;
import org.margin.server.social.margin.models.MarginRole;
import org.margin.server.social.margin.models.dtos.MarginMemberDTO;
import org.margin.server.social.margin.models.dtos.UpdateMarginDTO;
import org.margin.server.social.margin.repositories.MarginMemberRepository;
import org.margin.server.social.margin.repositories.MarginRepository;
import org.margin.server.social.margin.service.MarginMapper;
import org.margin.server.social.margin.service.MarginService;
import org.margin.server.social.models.Visibility;
import org.margin.server.social.space.services.SpacesService;
import org.margin.server.social.api.MarginIconStore;
import org.margin.server.social.api.MarginSubscriptionPolicy;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.users.services.UserService;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.margin.server.unittest.utils.UserTestUtils.createUser;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarginServiceTest {
    public static final String TEST_MARGIN = "Test Margin";
    public static final String TEST_DESCRIPTION = "Test Description";
    public static final Visibility VISIBILITY = Visibility.PUBLIC;

    @Mock
    private MarginRepository marginRepository;
    @Mock
    private UserLookup userLookup;
    @Mock
    private MarginMemberRepository marginMemberRepository;
    @Mock
    private MarginIconStore marginIconStore;
    @Mock
    private SpacesService spacesService;
    @Mock
    private UserService userService;
    @Mock
    private MarginMapper marginMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private MarginSubscriptionPolicy marginSubscriptionPolicy;

    @InjectMocks
    private MarginService marginService;

    @org.junit.jupiter.api.BeforeEach
    void stubUserLookup() {
        org.mockito.Mockito.lenient().when(userLookup.dtoOf(org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(i -> new org.margin.server.users.models.dtos.UserDTO(
                        i.getArgument(0), "u", null, null, null, false));
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
    }

    @Test
    void shouldCreateMargin() {
        stubMarginSave();

        marginService.createMargin(TEST_MARGIN, TEST_DESCRIPTION, VISIBILITY, null, createUser(1L, "testuser").getId());

        ArgumentCaptor<Margin> marginCaptor = ArgumentCaptor.forClass(Margin.class);
        verify(marginRepository, times(1)).save(marginCaptor.capture());

        Margin capturedMargin = marginCaptor.getValue();
        assertThat(capturedMargin.getName()).isEqualTo(TEST_MARGIN);
        assertThat(capturedMargin.getDescription()).isEqualTo(TEST_DESCRIPTION);
        assertThat(capturedMargin.getVisibility()).isEqualTo(VISIBILITY);

        verify(marginIconStore, never()).save(any());
    }

    @Test
    void shouldUpdateMarginNameAndDescription() {
        Margin existing = new Margin();
        existing.setId(1L);
        existing.setName("Old Name");
        existing.setDescription("Old Description");

        when(marginRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(marginRepository.save(any(Margin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateMarginDTO dto = new UpdateMarginDTO(1L, "New Name", "New Description");
        marginService.updateMarginAsDto(dto, null);

        ArgumentCaptor<Margin> captor = ArgumentCaptor.forClass(Margin.class);
        verify(marginRepository).save(captor.capture());

        Margin saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("New Name");
        assertThat(saved.getDescription()).isEqualTo("New Description");
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
        when(marginIconStore.save(profilePicture)).thenReturn(expectedUrl);

        marginService.createMargin(TEST_MARGIN, TEST_DESCRIPTION, VISIBILITY, profilePicture, createUser(1L, "testuser").getId());

        verify(marginIconStore, times(1)).save(profilePicture);

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

        marginService.createMargin(TEST_MARGIN, TEST_DESCRIPTION, VISIBILITY, emptyFile, createUser(1L, "testuser").getId());

        verify(marginIconStore, never()).save(any());

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

        when(marginIconStore.save(profilePicture))
                .thenThrow(new RuntimeException("Storage failed"));

        assertThrows(RuntimeException.class, () ->
                marginService.createMargin(TEST_MARGIN, TEST_DESCRIPTION, VISIBILITY, profilePicture, createUser(1L, "testuser").getId())
        );

        verify(marginRepository, never()).save(any());
    }

    @Test
    void promotingMemberToOwner_demotesPreviousOwnerToAdmin() {
        Margin margin = new Margin();
        margin.setId(1L);

        User ownerUser = createUser(1L, "owner");
        User targetUser = createUser(2L, "target");

        MarginMember owner = new MarginMember();
        owner.setUserId(ownerUser.getId());
        owner.setMargin(margin);
        owner.setRole(MarginRole.OWNER);

        MarginMember target = new MarginMember();
        target.setUserId(targetUser.getId());
        target.setMargin(margin);
        target.setRole(MarginRole.MEMBER);

        margin.setMembers(new ArrayList<>(List.of(owner, target)));

        when(marginRepository.findById(1L)).thenReturn(Optional.of(margin));
        when(marginRepository.save(any(Margin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MarginMemberDTO dto = new MarginMemberDTO(new UserDTO(targetUser, false), MarginRole.OWNER, Instant.now());

        marginService.updateMarginMemberRole(1L, 1L, dto);

        assertThat(target.getRole()).isEqualTo(MarginRole.OWNER);
        assertThat(owner.getRole()).isEqualTo(MarginRole.ADMIN);
    }
}