package org.margin.server.unittest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.users.api.UserLookup;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.channel.repositories.ChannelRepository;
import org.margin.server.social.channel.services.ChannelService;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.entities.MarginMember;
import org.margin.server.social.margin.repositories.MarginMemberRepository;
import org.margin.server.social.margin.service.MarginMapper;
import org.margin.server.social.channel.models.ChannelDTO;
import org.margin.server.social.channel.models.ChannelType;
import org.margin.server.social.models.Visibility;
import org.margin.server.shared.voice.VoiceParticipantLookup;
import org.margin.server.social.space.exceptions.SpaceNotFoundException;
import org.margin.server.social.space.exceptions.UserNotInMargin;
import org.margin.server.social.space.models.Space;
import org.margin.server.social.space.models.SpaceMember;
import org.margin.server.social.space.models.SpaceRole;
import org.margin.server.social.space.models.dtos.CreateSpaceDTO;
import org.margin.server.social.space.models.dtos.SpaceDTO;
import org.margin.server.social.space.repositories.SpaceMemberRepository;
import org.margin.server.social.space.repositories.SpacesRepository;
import org.margin.server.social.space.services.SpacesActions;
import org.margin.server.presence.PresenceService;
import org.margin.server.social.space.services.SpacesCreationService;
import org.margin.server.social.space.services.SpacesService;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;
import static org.margin.server.unittest.utils.UserTestUtils.*;
import static org.margin.server.unittest.utils.SpaceTestUtils.createSpace;

@ExtendWith(MockitoExtension.class)
class SpacesServiceTest {

    @Mock
    private SpacesRepository spacesRepository;
    @Mock
    private UserLookup userLookup;
    @Mock
    private SpaceMemberRepository spaceMemberRepository;
    @Mock
    private ChannelRepository channelRepository;
    @Mock
    private ChannelService channelService;
    @Mock
    private MarginMemberRepository marginMemberRepository;
    @Mock
    private SpacesCreationService spacesCreationService;
    @Mock
    private MarginMapper marginMapper;
    @Mock
    private PresenceService presenceService;
    @Mock
    private SpacesActions spacesActions;
    @Mock
    private VoiceParticipantLookup voiceParticipantLookup;

    @InjectMocks
    private SpacesService spacesService;

    @org.junit.jupiter.api.BeforeEach
    void stubUserLookup() {
        org.mockito.Mockito.lenient().when(userLookup.dtoOf(org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(i -> new org.margin.server.users.models.dtos.UserDTO(
                        i.getArgument(0), "u", null, null, null, false));
    }

    private Margin testMargin(User... members) {
        Margin margin = new Margin();
        List<MarginMember> marginMembers = new ArrayList<>();
        for (User u : members) {
            MarginMember mm = new MarginMember();
            mm.setUserId(u.getId());
            marginMembers.add(mm);
        }
        margin.setMembers(marginMembers);
        return margin;
    }

    @Test
    @DisplayName("createNewSpace should throw exception if name exists")
    void createNewSpace_DuplicateName_ThrowsException() {
        CreateSpaceDTO dto = new CreateSpaceDTO("General", "Desc", Visibility.PUBLIC, 1L);
        Margin margin = testMargin();
        margin.setId(1L);

        when(spacesRepository.getSpaceByName("General", margin.getId()))
                .thenReturn(Optional.of(new Space()));

        User user = createUser(1L);
        assertThrows(DuplicateKeyException.class,
                () -> spacesService.createNewSpace(dto, user.getId(), margin));
    }

    @Test
    @DisplayName("createNewSpace should create space, add creator as ADMIN, and create General Chat channel")
    void createNewSpace_Success() {
        CreateSpaceDTO dto = new CreateSpaceDTO("General", "Desc", Visibility.PUBLIC, 1L);
        User user = createUser(1L);

        Margin margin = testMargin(user);
        margin.setId(5L);

        Space space = createSpace(10L, margin);
        space.setChannels(new ArrayList<>());
        space.setMembers(new ArrayList<>());

        when(spacesRepository.getSpaceByName("General", margin.getId()))
                .thenReturn(Optional.empty());
        when(spacesCreationService.create("General", "Desc", Visibility.PUBLIC, margin))
                .thenReturn(space);
        when(channelService.createNewChannel(any(Space.class), eq("General Chat"), anyString()))
                .thenReturn(new Channel());

        SpaceMember spaceMember = new SpaceMember();
        spaceMember.setUserId(user.getId());
        spaceMember.setRole(SpaceRole.ADMIN);
        when(spacesActions.addUserToSpace(user.getId(), space, SpaceRole.ADMIN)).thenReturn(spaceMember);
        when(marginMemberRepository.findByMargin_Id(margin.getId())).thenReturn(List.of());

        SpaceDTO mappedDto = new SpaceDTO(
                10L, "General", "Desc", margin.getId(), Visibility.PUBLIC,
                List.of(), List.of()
        );
        when(marginMapper.spaceToDto(space)).thenReturn(mappedDto);

        SpaceDTO result = spacesService.createNewSpace(dto, user.getId(), margin);

        assertNotNull(result);
        assertEquals(10L, result.spaceId());
        verify(spacesCreationService).create("General", "Desc", Visibility.PUBLIC, margin);
        verify(channelService).createNewChannel(any(Space.class), eq("General Chat"), anyString());
        verify(spacesActions).addUserToSpace(user.getId(), space, SpaceRole.ADMIN);
    }

    @Test
    @DisplayName("addNewUserToSpace should delegate to SpacesActions")
    void addNewUserToSpace_Success() {
        Space space = createSpace(1L);
        User newUser = createUser(2L);

        SpaceMember spaceMember = new SpaceMember();
        spaceMember.setUserId(newUser.getId());
        spaceMember.setRole(SpaceRole.MEMBER);
        spaceMember.setSpace(space);

        when(spacesRepository.findById(space.getId())).thenReturn(Optional.of(space));
        when(spacesActions.addUserToSpace(newUser.getId(), space, SpaceRole.MEMBER)).thenReturn(spaceMember);

        spacesService.addNewUserToSpace(newUser.getId(), space.getId(), SpaceRole.MEMBER);

        verify(spacesActions).addUserToSpace(newUser.getId(), space, SpaceRole.MEMBER);
    }

    @Test
    @DisplayName("addNewUserToSpace should propagate DuplicateKeyException from SpacesActions")
    void addNewUserToSpace_RejectsDuplicate() {
        Space space = createSpace(1L);
        User existingUser = createUser(1L);

        when(spacesRepository.findById(space.getId())).thenReturn(Optional.of(space));
        when(spacesActions.addUserToSpace(existingUser.getId(), space, SpaceRole.MEMBER))
                .thenThrow(new DuplicateKeyException("Can't add duplicate space member"));

        assertThrows(DuplicateKeyException.class, () ->
                spacesService.addNewUserToSpace(existingUser.getId(), space.getId(), SpaceRole.MEMBER)
        );
    }

    @Test
    @DisplayName("addNewUserToSpace should propagate UserNotInMargin from SpacesActions")
    void addNewUserToSpace_RejectsNonMarginMember() {
        Space space = createSpace(1L);
        User outsider = createUser(3L);

        when(spacesRepository.findById(space.getId())).thenReturn(Optional.of(space));
        when(spacesActions.addUserToSpace(outsider.getId(), space, SpaceRole.MEMBER))
                .thenThrow(new UserNotInMargin(outsider.getId()));

        assertThrows(UserNotInMargin.class, () ->
                spacesService.addNewUserToSpace(outsider.getId(), space.getId(), SpaceRole.MEMBER)
        );
    }

    @Test
    @DisplayName("deleteSpace should delegate to SpacesActions")
    void deleteSpace_Success() {
        spacesService.deleteSpace(1L);

        verify(spacesActions).deleteSpace(1L);
    }

    @Test
    @DisplayName("getById should throw when space not found")
    void getById_NotFound() {
        when(spacesRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(SpaceNotFoundException.class, () -> spacesService.getById(99L));
    }

    private Space stubSpaceWithChannels(Long spaceId, Long... channelIds) {
        Margin margin = testMargin();
        margin.setId(1L);
        Space space = createSpace(spaceId, margin);

        List<ChannelDTO> channels = new ArrayList<>();
        for (Long channelId : channelIds) {
            channels.add(new ChannelDTO(channelId, "c" + channelId, "", channelId,
                    ChannelType.Communication, spaceId));
        }

        when(marginMapper.spaceToDto(space)).thenReturn(new SpaceDTO(
                spaceId, "Space " + spaceId, "", 1L, Visibility.PUBLIC, channels, List.of()));
        return space;
    }

    private static UserDTO userDto(Long id) {
        return new UserDTO(id, "u" + id, null, null, null, false);
    }

    @Test
    @DisplayName("getSpacesForUserInMargin should fetch voice participants for every channel in one lookup")
    void getSpacesForUserInMargin_FetchesVoiceParticipantsInOneLookup() {
        Space first = stubSpaceWithChannels(10L, 100L, 101L);
        Space second = stubSpaceWithChannels(20L, 200L);

        when(spacesRepository.findVisibleSpacesForUser(1L, 1L)).thenReturn(List.of(first, second));
        when(voiceParticipantLookup.participantIdsByChannel(List.of(100L, 101L, 200L)))
                .thenReturn(Map.of(100L, List.of(7L), 200L, List.of(8L, 9L)));
        when(userLookup.dtosOf(anyCollection()))
                .thenReturn(List.of(userDto(7L), userDto(8L), userDto(9L)));

        List<SpaceDTO> spaces = spacesService.getSpacesForUserInMargin(1L, 1L);

        assertEquals(List.of(7L),
                spaces.get(0).channels().get(0).voiceParticipants().stream().map(UserDTO::id).toList());
        assertEquals(List.of(), spaces.get(0).channels().get(1).voiceParticipants());
        assertEquals(List.of(8L, 9L),
                spaces.get(1).channels().get(0).voiceParticipants().stream().map(UserDTO::id).toList());

        verify(voiceParticipantLookup, times(1)).participantIdsByChannel(anyCollection());
        verify(userLookup, times(1)).dtosOf(anyCollection());
    }

    @Test
    @DisplayName("getSpacesForUserInMargin should resolve each participant user only once")
    void getSpacesForUserInMargin_DeduplicatesParticipantLookups() {
        Space space = stubSpaceWithChannels(10L, 100L, 101L);

        when(spacesRepository.findVisibleSpacesForUser(1L, 1L)).thenReturn(List.of(space));
        when(voiceParticipantLookup.participantIdsByChannel(List.of(100L, 101L)))
                .thenReturn(Map.of(100L, List.of(7L), 101L, List.of(7L)));
        when(userLookup.dtosOf(List.of(7L))).thenReturn(List.of(userDto(7L)));

        List<SpaceDTO> spaces = spacesService.getSpacesForUserInMargin(1L, 1L);

        assertEquals(7L, spaces.get(0).channels().get(0).voiceParticipants().get(0).id());
        assertEquals(7L, spaces.get(0).channels().get(1).voiceParticipants().get(0).id());
        verify(userLookup).dtosOf(List.of(7L));
    }

    @Test
    @DisplayName("getSpacesForUserInMargin should not resolve users when nobody is in voice")
    void getSpacesForUserInMargin_SkipsUserLookupWhenNobodyInVoice() {
        Space space = stubSpaceWithChannels(10L, 100L);

        when(spacesRepository.findVisibleSpacesForUser(1L, 1L)).thenReturn(List.of(space));
        when(voiceParticipantLookup.participantIdsByChannel(List.of(100L))).thenReturn(Map.of());

        List<SpaceDTO> spaces = spacesService.getSpacesForUserInMargin(1L, 1L);

        assertEquals(List.of(), spaces.get(0).channels().get(0).voiceParticipants());
        verify(userLookup, never()).dtosOf(anyCollection());
    }
}
