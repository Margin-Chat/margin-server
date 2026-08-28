package org.margin.server.meetings.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.margin.server.meetings.models.dtos.ClaimGuestRequest;
import org.margin.server.meetings.models.dtos.GuestSessionRequest;
import org.margin.server.meetings.models.dtos.GuestSessionResponse;
import org.margin.server.meetings.models.dtos.MeetingPreviewDTO;
import org.margin.server.meetings.security.MeetingGuestPrincipal;
import org.margin.server.meetings.models.MeetingInviteStatus;
import org.margin.server.meetings.models.dtos.RsvpRequest;
import org.margin.server.meetings.services.MeetingGuestService;
import org.margin.server.meetings.models.dtos.MeetingJoinResponse;
import org.margin.server.meetings.services.MeetingSchedulingService;
import org.margin.server.meetings.services.MeetingService;
import org.margin.server.shared.exceptions.TooManyRequestsException;
import org.margin.server.shared.ratelimit.RateLimitConfig;
import org.margin.server.shared.ratelimit.RateLimitService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/meetings")
public class MeetingGuestController {

    private final MeetingGuestService guestService;
    private final RateLimitService rateLimitService;
    private final MeetingSchedulingService schedulingService;
    private final MeetingService meetingService;

    public MeetingGuestController(MeetingGuestService guestService,
                                  RateLimitService rateLimitService,
                                  MeetingSchedulingService schedulingService,
                                  MeetingService meetingService) {
        this.guestService = guestService;
        this.rateLimitService = rateLimitService;
        this.schedulingService = schedulingService;
        this.meetingService = meetingService;
    }

    @PostMapping("/{code}/preview")
    public MeetingPreviewDTO preview(@PathVariable String code, HttpServletRequest request) {
        rateLimit(request, "meeting-preview:", RateLimitConfig.meetingPreview(),
                "Too many requests. Try again later.");
        return guestService.preview(code);
    }

    @PostMapping("/{code}/guest-session")
    public GuestSessionResponse guestSession(@PathVariable String code,
                                             @RequestBody GuestSessionRequest body,
                                             HttpServletRequest request) {
        rateLimit(request, "guest-session:", RateLimitConfig.guestSession(),
                "Too many join attempts. Try again later.");
        return guestService.createGuestSession(code, body == null ? null : body.displayName());
    }

    @PostMapping("/{code}/guest-join")
    public MeetingJoinResponse guestJoin(@PathVariable String code,
                                         @AuthenticationPrincipal MeetingGuestPrincipal guest) {
        return meetingService.joinAsGuest(code, guest.userId());
    }

    @PostMapping("/rsvp")
    public MeetingInviteStatus rsvp(@RequestBody RsvpRequest body, HttpServletRequest request) {
        rateLimit(request, "meeting-rsvp:", RateLimitConfig.meetingPreview(),
                "Too many requests. Try again later.");
        return schedulingService.respond(body.inviteToken(), body.accepted());
    }

    @GetMapping("/session")
    public GuestSessionResponse resume(@AuthenticationPrincipal MeetingGuestPrincipal guest) {
        return guestService.resume(guest);
    }

    @PostMapping("/claim")
    public void claim(@RequestBody ClaimGuestRequest body,
                      @AuthenticationPrincipal MeetingGuestPrincipal guest) {
        guestService.claim(guest, body);
    }

    private void rateLimit(HttpServletRequest request, String prefix,
                           io.github.bucket4j.Bandwidth bandwidth, String message) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null) ip = request.getRemoteAddr();

        if (!rateLimitService.tryConsume(prefix + ip, bandwidth)) {
            throw new TooManyRequestsException(message);
        }
    }
}
