package org.margin.server.meetings.controllers;

import org.margin.server.meetings.models.dtos.CreateMeetingRequest;
import org.margin.server.meetings.models.dtos.EligibleMarginDTO;
import org.margin.server.meetings.models.dtos.MeetingDTO;
import org.margin.server.meetings.models.dtos.MeetingJoinResponse;
import org.margin.server.meetings.services.MeetingService;
import org.margin.server.shared.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/meetings")
public class MeetingController {

    private final MeetingService meetingService;

    public MeetingController(MeetingService meetingService) {
        this.meetingService = meetingService;
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

    @PostMapping("/{code}/end")
    public MeetingDTO end(@PathVariable String code,
                          @AuthenticationPrincipal AuthenticatedUser user) {
        return meetingService.end(code, user.id());
    }
}
