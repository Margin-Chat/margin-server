package org.margin.server.unittest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.channel.repositories.ChannelRepository;
import org.margin.server.social.channel.services.ChannelService;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.entities.MarginMember;
import org.margin.server.social.margin.repositories.MarginMemberRepository;
import org.margin.server.social.margin.service.MarginMapper;
import org.margin.server.social.models.Visibility;
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
import org.margin.server.social.space.services.SpacesCreationService;
import org.margin.server.social.space.services.SpacesService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpacesServiceTest {

    @Mock
    private SpacesRepository spacesRepository;
    @Mock
    private SpaceMemberRepository spaceMemberRepository;
    @Mock
    private ChannelRepository channelRepository;
    @Mock
    private ChannelService channelService;
    @Mock
    private MarginMemberRepository marginMemberRepository;
    @Mock
    private WebSocketDeliveryService webSocketDeliveryService;
    @Mock
    private SpacesCreationService spacesCreationService;
    @Mock
    private MarginMapper marginMapper;
    @Mock
    private ConnectionManager connectionManager;
    @Mock
    private SpacesActions spacesActions;

    @InjectMocks
    private SpacesService spacesService;

    private User testUser(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Margin testMargin(User... members) {
        Margin margin = new Margin();
        List<MarginMember> marginMembers = new ArrayList<>();
        for (User u : members) {
            MarginMember mm = new MarginMember();
            mm.setUser(u);
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

        User user = testUser(1L);
        assertThrows(DuplicateKeyException.class,
                () -> spacesService.createNewSpace(dto, user, margin));
    }

    @Test
    @DisplayName("createNewSpace should create space, add creator as ADMIN, and create General Chat channel")
    void createNewSpace_Success() {
        CreateSpaceDTO dto = new CreateSpaceDTO("General", "Desc", Visibility.PUBLIC, 1L);
        User user = testUser(1L);

        Margin margin = testMargin(user);
        margin.setId(5L);

        Space space = new Space();
        space.setId(10L);
        space.setMargin(margin);
        space.setChannels(new ArrayList<>());
        space.setMembers(new ArrayList<>());

        when(spacesRepository.getSpaceByName("General", margin.getId()))
                .thenReturn(Optional.empty());
        when(spacesCreationService.create("General", "Desc", Visibility.PUBLIC, margin))
                .thenReturn(space);
        when(channelService.createNewChannel(any(Space.class), eq("General Chat"), anyString()))
                .thenReturn(new Channel());

        SpaceMember spaceMember = new SpaceMember();
        spaceMember.setUser(user);
        spaceMember.setRole(SpaceRole.ADMIN);
        when(spacesActions.addUserToSpace(user, space, SpaceRole.ADMIN)).thenReturn(spaceMember);
        when(marginMemberRepository.findByMargin_Id(margin.getId())).thenReturn(List.of());

        SpaceDTO mappedDto = new SpaceDTO(
                10L, "General", "Desc", margin.getId(), Visibility.PUBLIC,
                List.of(), List.of()
        );
        when(marginMapper.spaceToDto(space)).thenReturn(mappedDto);

        SpaceDTO result = spacesService.createNewSpace(dto, user, margin);

        assertNotNull(result);
        assertEquals(10L, result.spaceId());
        verify(spacesCreationService).create("General", "Desc", Visibility.PUBLIC, margin);
        verify(channelService).createNewChannel(any(Space.class), eq("General Chat"), anyString());
        verify(spacesActions).addUserToSpace(user, space, SpaceRole.ADMIN);
    }

    @Test
    @DisplayName("addNewUserToSpace should delegate to SpacesActions")
    void addNewUserToSpace_Success() {
        Space space = new Space();
        space.setId(1L);
        User newUser = testUser(2L);

        SpaceMember spaceMember = new SpaceMember();
        spaceMember.setUser(newUser);
        spaceMember.setRole(SpaceRole.MEMBER);
        spaceMember.setSpace(space);

        when(spacesRepository.findById(space.getId())).thenReturn(Optional.of(space));
        when(spacesActions.addUserToSpace(newUser, space, SpaceRole.MEMBER)).thenReturn(spaceMember);
        when(connectionManager.isUserOnline(newUser.getId())).thenReturn(false);

        spacesService.addNewUserToSpace(newUser, space.getId(), SpaceRole.MEMBER);

        verify(spacesActions).addUserToSpace(newUser, space, SpaceRole.MEMBER);
    }

    @Test
    @DisplayName("addNewUserToSpace should propagate DuplicateKeyException from SpacesActions")
    void addNewUserToSpace_RejectsDuplicate() {
        Space space = new Space();
        space.setId(1L);
        User existingUser = testUser(1L);

        when(spacesRepository.findById(space.getId())).thenReturn(Optional.of(space));
        when(spacesActions.addUserToSpace(existingUser, space, SpaceRole.MEMBER))
                .thenThrow(new DuplicateKeyException("Can't add duplicate space member"));

        assertThrows(DuplicateKeyException.class, () ->
                spacesService.addNewUserToSpace(existingUser, space.getId(), SpaceRole.MEMBER)
        );
    }

    @Test
    @DisplayName("addNewUserToSpace should propagate UserNotInMargin from SpacesActions")
    void addNewUserToSpace_RejectsNonMarginMember() {
        Space space = new Space();
        space.setId(1L);
        User outsider = testUser(3L);

        when(spacesRepository.findById(space.getId())).thenReturn(Optional.of(space));
        when(spacesActions.addUserToSpace(outsider, space, SpaceRole.MEMBER))
                .thenThrow(new UserNotInMargin(outsider.getId()));

        assertThrows(UserNotInMargin.class, () ->
                spacesService.addNewUserToSpace(outsider, space.getId(), SpaceRole.MEMBER)
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
}
