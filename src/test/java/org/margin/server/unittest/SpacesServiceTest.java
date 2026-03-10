package org.margin.server.unittest;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.channel.repositories.ChannelRepository;
import org.margin.server.social.channel.services.ChannelService;
import org.margin.server.social.channel.channel.Channel;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.margin.models.Margin;
import org.margin.server.social.margin.models.MarginMember;
import org.margin.server.social.margin.repositories.MarginMemberRepository;
import org.margin.server.social.models.Visibility;
import org.margin.server.social.space.models.Space;
import org.margin.server.social.space.models.SpaceMember;
import org.margin.server.social.space.models.dtos.CreateSpaceDTO;
import org.margin.server.social.space.repositories.SpaceMemberRepository;
import org.margin.server.social.space.repositories.SpacesRepository;
import org.margin.server.social.space.services.SpacesService;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.users.repositories.UserRepository;
import org.mockito.ArgumentCaptor;
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

    @Mock private SpacesRepository spacesRepository;
    @Mock private SpaceMemberRepository spaceMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private ChannelRepository channelRepository;
    @Mock private ConversationMemberRepository conversationMemberRepository;
    @Mock private ChannelService channelService;
    @Mock private EntityManager entityManager;
    @Mock private MarginMemberRepository marginMemberRepository;

    @InjectMocks
    private SpacesService spacesService;

    // --- Helpers ---

    private User testUser(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private UserDTO testUserDTO(long id) {
        return new UserDTO(testUser(id), false);
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

        when(spacesRepository.getSpaceByName("General", margin.getId())).thenReturn(Optional.of(new Space()));

        assertThrows(DuplicateKeyException.class,
                () -> spacesService.createNewSpace(dto, testUserDTO(1L), margin));
    }

    @Test
    @DisplayName("createNewSpace should create space, add creator as ADMIN, and create General Chat channel")

    void createNewSpace_Success() {
        CreateSpaceDTO dto = new CreateSpaceDTO("General", "Desc", Visibility.PUBLIC, 1L);
        UserDTO userDTO = testUserDTO(1L);
        User user = testUser(1L);
        Margin margin = testMargin(user);


        when(spacesRepository.getSpaceByName("General", margin.getId())).thenReturn(Optional.empty());
        when(spacesRepository.save(any(Space.class))).thenAnswer(i -> {
            Space s = i.getArgument(0);
            s.setId(10L);
            s.setChannels(new ArrayList<>());
            return s;
        });

        // addNewSpaceMemberToSpace does a findById on user and space
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(spacesRepository.findById(10L)).thenAnswer(i -> {
            Space s = new Space();
            s.setId(10L);
            s.setMargin(margin);
            s.setChannels(new ArrayList<>());
            return Optional.of(s);
        });
        when(spaceMemberRepository.existsSpaceMemberByUserAndSpace(any(), any())).thenReturn(false);
        when(spaceMemberRepository.save(any(SpaceMember.class))).thenAnswer(i -> i.getArgument(0));

        when(channelService.createChannel(any(Space.class), eq("General Chat"), anyString()))
                .thenReturn(new Channel());

        Space result = spacesService.createNewSpace(dto, userDTO, margin);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        verify(spacesRepository).save(any(Space.class));
        verify(spaceMemberRepository).save(any(SpaceMember.class));
        verify(channelService).createChannel(any(Space.class), eq("General Chat"), anyString());
    }

    @Test
    @DisplayName("addUsersToSpace should not add existing users")
    void addUsersToSpace_AvoidsDuplicates() {
        Long spaceId = 1L;
        Space space = new Space();
        User existingUser = testUser(1L);
        User newUser = testUser(2L);

        SpaceMember sm = new SpaceMember();
        sm.setUser(existingUser);
        space.setMembers(List.of(sm));

        when(spacesRepository.findById(spaceId)).thenReturn(Optional.of(space));

        spacesService.addUsersToSpace(spaceId, List.of(existingUser, newUser));

        ArgumentCaptor<List<SpaceMember>> captor = ArgumentCaptor.forClass(List.class);
        verify(spaceMemberRepository).saveAll(captor.capture());

        List<SpaceMember> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals(2L, saved.get(0).getUser().getId());
    }

    @Test
    @DisplayName("deleteSpace should delete members, channels, and the space itself")
    void deleteSpace_Success() {
        Long spaceId = 1L;
        Space space = new Space();

        when(spacesRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(spaceMemberRepository.findSpaceMemberBySpace(space)).thenReturn(List.of(new SpaceMember()));
        when(channelRepository.findChannelBySpace(space)).thenReturn(List.of(new Channel()));

        spacesService.deleteSpace(spaceId);

        verify(spaceMemberRepository).deleteAll(anyList());
        verify(channelRepository).deleteAll(anyList());
        verify(spacesRepository).deleteById(spaceId);
    }

    @Test
    @DisplayName("getById should throw when space not found")
    void getById_NotFound() {
        when(spacesRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> spacesService.getById(99L));
    }
}