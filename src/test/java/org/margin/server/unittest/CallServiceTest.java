package org.margin.server.unittest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.calls.exceptions.CallNotFoundException;
import org.margin.server.social.calls.models.Call;
import org.margin.server.social.calls.models.CallStatus;
import org.margin.server.social.calls.models.CallType;
import org.margin.server.social.calls.repositories.CallRepository;
import org.margin.server.social.calls.services.CallService;
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
import static org.margin.server.unittest.utils.UserTestUtils.*;

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

        Call savedCall = new Call(fromUser, toUser, CallStatus.RINGING, CallType.VIDEO);
        savedCall.setId(100L);

        when(callRepository.save(any(Call.class))).thenReturn(savedCall);

        Call result = callService.createCall(fromUser, toUser, CallStatus.RINGING, CallType.VIDEO);

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
        Call call = new Call();
        call.setId(1L);
        call.setStatus(CallStatus.ACCEPTED);

        callService.endCall(call, 120);

        ArgumentCaptor<Call> captor = ArgumentCaptor.forClass(Call.class);
        verify(callRepository).save(captor.capture());

        Call endedCall = captor.getValue();
        assertEquals(CallStatus.ENDED, endedCall.getStatus());
        assertEquals(120, endedCall.getDurationSeconds());
        assertNotNull(endedCall.getEndedAt());
    }

    @Test
    void rejectCall_throwsExceptionWhenCallNotFound() {
        when(callRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(CallNotFoundException.class,
                () -> callService.rejectCall(999L));
    }

}