package org.margin.server.meetings.controllers;

import org.margin.server.meetings.models.dtos.CreateMeetingRequest;
import org.margin.server.meetings.models.dtos.EligibleMarginDTO;
import org.margin.server.meetings.models.dtos.MeetingDTO;
import org.margin.server.meetings.models.dtos.MeetingJoinResponse;
import org.margin.server.meetings.models.dtos.AdmissionRequest;
import org.margin.server.meetings.models.dtos.MeetingParticipantDTO;
import org.margin.server.meetings.services.MeetingAdmissionService;
import org.margin.server.meetings.services.MeetingService;
import org.margin.server.shared.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/meetings")
public class MeetingController {

    private final MeetingService meetingService;
    private final MeetingAdmissionService admissionService;

    public MeetingController(MeetingService meetingService, MeetingAdmissionService admissionService) {
        this.meetingService = meetingService;
        this.admissionService = admissionService;
    }

    @PostMapping
    public MeetingDTO create(@RequestBody CreateMeetingRequest request,
                             @AuthenticationPrincipal AuthenticatedUser user) {
        return meetingService.createInstantMeeting(user.id(), request);
    }

    @GetMapping("/eligible-margins")
    public List<EligibleMarginDTO> eligibleMargins(@AuthenticationPrincipal AuthenticatedUser user) {
        return meetingService.eligibleMargins(user.id());
    }

    @GetMapping("/{code}")
    public MeetingDTO get(@PathVariable String code,
                          @AuthenticationPrincipal AuthenticatedUser user) {
        return meetingService.getByCode(code, user.id());
    }

    @PostMapping("/{code}/join")
    public MeetingJoinResponse join(@PathVariable String code,
                                    @AuthenticationPrincipal AuthenticatedUser user) {
        return meetingService.join(code, user.id());
    }

    @GetMapping("/{code}/lobby")
    public List<MeetingParticipantDTO> lobby(@PathVariable String code,
                                             @AuthenticationPrincipal AuthenticatedUser user) {
        return admissionService.waiting(code, user.id());
    }

    @PostMapping("/{code}/admit")
    public void admit(@PathVariable String code,
                      @RequestBody AdmissionRequest request,
                      @AuthenticationPrincipal AuthenticatedUser user) {
        admissionService.admit(code, user.id(), request.participantId());
    }

    @PostMapping("/{code}/deny")
    public void deny(@PathVariable String code,
                     @RequestBody AdmissionRequest request,
                     @AuthenticationPrincipal AuthenticatedUser user) {
        admissionService.deny(code, user.id(), request.participantId());
    }

    @PostMapping("/{code}/remove")
    public void remove(@PathVariable String code,
                       @RequestBody AdmissionRequest request,
                       @AuthenticationPrincipal AuthenticatedUser user) {
        admissionService.remove(code, user.id(), request.participantId());
    }

    @PostMapping("/{code}/end")
    public MeetingDTO end(@PathVariable String code,
                          @AuthenticationPrincipal AuthenticatedUser user) {
        return meetingService.end(code, user.id());
    }
}
