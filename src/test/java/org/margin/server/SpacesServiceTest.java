package org.margin.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.channel.ChannelRepository;
import org.margin.server.social.channel.channel.Channel;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.margin.MarginService;
import org.margin.server.social.margin.models.Margin;
import org.margin.server.social.models.Visibility;
import org.margin.server.social.space.models.Space;
import org.margin.server.social.space.models.SpaceMember;
import org.margin.server.social.space.models.dtos.CreateSpaceDTO;
import org.margin.server.social.space.repositories.SpaceMemberRepository;
import org.margin.server.social.space.repositories.SpacesRepository;
import org.margin.server.social.space.services.SpacesService;
import org.margin.server.users.models.User;
import org.margin.server.users.repositories.UserRepository;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpacesServiceTest {

    @Mock private SpacesRepository spacesRepository;
    @Mock private MarginService marginService;
    @Mock private SpaceMemberRepository spaceMemberRepository;
    @Mock private ChannelRepository channelRepository;

    @InjectMocks
    private SpacesService spacesService;

    @Test
    @DisplayName("createNewSpace should throw exception if name exists")
    void createNewSpace_DuplicateName_ThrowsException() {
        CreateSpaceDTO dto = new CreateSpaceDTO("General", "Desc", Visibility.PUBLIC ,1L);
        when(spacesRepository.getSpaceByName("General")).thenReturn(Optional.of(new Space()));

        assertThrows(DuplicateKeyException.class, () -> spacesService.createNewSpace(dto));
    }

    @Test
    @DisplayName("createNewSpace should create and return new space")
    void createNewSpace_Success() {
        // Arrange
        CreateSpaceDTO dto = new CreateSpaceDTO("General", "Desc", Visibility.PUBLIC ,1L);
        Margin margin = new Margin();

        // FIX: Match the name in the DTO or use anyString() to avoid PotentialStubbingProblem
        when(spacesRepository.getSpaceByName("General")).thenReturn(Optional.empty());
        when(marginService.getMargin(1L)).thenReturn(margin);

        // Mock save to act as if ID was generated
        when(spacesRepository.save(any(Space.class))).thenAnswer(i -> {
            Space s = i.getArgument(0);
            s.setId(10L);
            return s;
        });
        when(spacesRepository.findById(10L)).thenReturn(Optional.of(new Space()));

        // Act
        Space result = spacesService.createNewSpace(dto);

        // Assert
        assertNotNull(result);
        verify(spacesRepository).save(any(Space.class));
    }

    @Test
    @DisplayName("addUsersToSpace should not add existing users")
    void addUsersToSpace_AvoidsDuplicates() {
        // Arrange
        Long spaceId = 1L;
        Space space = new Space();
        User existingUser = new User(); existingUser.setId(1L);
        User newUser = new User(); newUser.setId(2L);

        SpaceMember sm = new SpaceMember(); sm.setUser(existingUser);
        space.setMembers(List.of(sm));

        when(spacesRepository.findById(spaceId)).thenReturn(Optional.of(space));

        // Act
        spacesService.addUsersToSpace(spaceId, List.of(existingUser, newUser));

        // Assert
        ArgumentCaptor<List<SpaceMember>> captor = ArgumentCaptor.forClass(List.class);
        verify(spaceMemberRepository).saveAll(captor.capture());

        List<SpaceMember> savedMembers = captor.getValue();
        assertEquals(1, savedMembers.size());
        // FIX: Assert type matches User.getId() type (Long)
        assertEquals(2L, savedMembers.get(0).getUser().getId());
    }

    @Test
    @DisplayName("deleteSpace should delete members, channels, and the space itself")
    void deleteSpace_Success() {
        // Arrange
        Long spaceId = 1L;
        Space space = new Space();
        when(spacesRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(spaceMemberRepository.findSpaceMemberBySpace(space)).thenReturn(List.of(new SpaceMember()));
        when(channelRepository.findChannelBySpace(space)).thenReturn(List.of(new Channel()));

        // Act
        spacesService.deleteSpace(spaceId);

        // Assert
        verify(spaceMemberRepository).deleteAll(anyList());
        verify(channelRepository).deleteAll(anyList());
        verify(spacesRepository).deleteById(spaceId);
    }

    @Test
    @DisplayName("getById should throw exception when space not found")
    void getById_NotFound() {
        when(spacesRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> spacesService.getById(99L));
    }
}