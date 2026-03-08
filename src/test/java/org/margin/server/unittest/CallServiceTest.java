package org.margin.server.unittest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.calls.models.Call;
import org.margin.server.social.calls.models.CallStatus;
import org.margin.server.social.calls.models.CallType;
import org.margin.server.social.calls.repositories.CallRepository;
import org.margin.server.social.calls.services.CallService;
import org.margin.server.social.calls.exceptions.CallNotFoundException;
import org.margin.server.users.models.User;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallServiceTest {
    @Mock
    private CallRepository callRepository;

    @InjectMocks
    private CallService callService;

    @Test
    void createCall_savesAndReturnsCall() {
        User fromUser = createUser(1L, "caller");
        User toUser = createUser(2L, "receiver");
        String sdp = "test-sdp-data";

        Call savedCall = new Call(fromUser, toUser, CallStatus.RINGING, CallType.VIDEO, sdp);
        savedCall.setId(100L);

        when(callRepository.save(any(Call.class))).thenReturn(savedCall);

        Call result = callService.createCall(fromUser, toUser, CallStatus.RINGING, CallType.VIDEO, sdp);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        verify(callRepository).save(any(Call.class));
    }

    @Test
    void updateCallStatus_updatesStatusSuccessfully() {
        Long callId = 1L;
        Call call = new Call();
        call.setId(callId);
        call.setStatus(CallStatus.RINGING);

        when(callRepository.findById(callId)).thenReturn(Optional.of(call));

        callService.updateCallStatus(callId, CallStatus.ACCEPTED);

        ArgumentCaptor<Call> captor = ArgumentCaptor.forClass(Call.class);
        verify(callRepository).save(captor.capture());

        Call updatedCall = captor.getValue();
        assertEquals(CallStatus.ACCEPTED, updatedCall.getStatus());
    }

    @Test
    void updateCallStatus_throwsExceptionWhenCallNotFound() {
        Long callId = 999L;
        when(callRepository.findById(callId)).thenReturn(Optional.empty());

        assertThrows(CallNotFoundException.class,
                () -> callService.updateCallStatus(callId, CallStatus.ACCEPTED));
    }

    @Test
    void endCall_updatesCallWithEndDetails() {
        Long callId = 1L;
        Integer durationSeconds = 120;

        Call call = new Call();
        call.setId(callId);
        call.setStatus(CallStatus.ACCEPTED);

        when(callRepository.findById(callId)).thenReturn(Optional.of(call));

        callService.endCall(callId, durationSeconds);

        ArgumentCaptor<Call> captor = ArgumentCaptor.forClass(Call.class);
        verify(callRepository).save(captor.capture());

        Call endedCall = captor.getValue();
        assertEquals(CallStatus.ENDED, endedCall.getStatus());
        assertEquals(durationSeconds, endedCall.getDurationSeconds());
        assertNotNull(endedCall.getEndedAt());
    }

    @Test
    void endCall_throwsExceptionWhenCallNotFound() {
        Long callId = 999L;
        when(callRepository.findById(callId)).thenReturn(Optional.empty());

        assertThrows(CallNotFoundException.class,
                () -> callService.endCall(callId, 120));
    }

    private User createUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}