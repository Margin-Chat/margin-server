package org.margin.server.meetings.services;

import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.email.EmailService;
import org.margin.server.meetings.entities.Meeting;
import org.margin.server.meetings.entities.MeetingInvite;
import org.margin.server.social.api.MarginLookup;
import org.margin.server.users.api.UserLookup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class MeetingInviteMailer {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm", Locale.ENGLISH);

    private final EmailService emailService;
    private final MeetingIcsBuilder icsBuilder;
    private final MarginLookup marginLookup;
    private final UserLookup userLookup;
    private final Executor mailExecutor;

    @Value("${margin.app.base-url:http://localhost:8080}")
    private String baseUrl;

    public MeetingInviteMailer(EmailService emailService,
                               MeetingIcsBuilder icsBuilder,
                               MarginLookup marginLookup,
                               UserLookup userLookup,
                               Executor mailExecutor) {
        this.emailService = emailService;
        this.icsBuilder = icsBuilder;
        this.marginLookup = marginLookup;
        this.userLookup = userLookup;
        this.mailExecutor = mailExecutor;
    }

    public void send(Meeting meeting, List<MeetingInvite> invites, boolean cancelled) {
        if (invites.isEmpty()) {
            return;
        }

        String inviterName = userLookup.summaryOf(meeting.getHostUserId()).displayName();
        String marginName = marginLookup.summaryOf(meeting.getMarginId()).name();
        String title = meeting.getTitle() == null ? "Meeting" : meeting.getTitle();
        String whenLine = whenLine(meeting);

        for (MeetingInvite invite : invites) {
            String address = addressFor(invite);
            if (address == null) {
                continue;
            }
            String joinUrl = joinUrl(meeting, invite);
            String body = emailService.buildMeetingInviteMail(
                    inviterName, title, marginName, whenLine, joinUrl, cancelled);
            String ics = icsBuilder.build(meeting, joinUrl, cancelled);
            String subject = (cancelled ? "Cancelled: " : "") + title;

            mailExecutor.execute(() -> {
                try {
                    emailService.sendEmailWithCalendar(address, subject, body, "meeting.ics", ics);
                } catch (MessagingException | RuntimeException e) {
                    log.warn("Could not send the meeting invite to {}: {}", address, e.getMessage());
                }
            });
        }
    }

    private String addressFor(MeetingInvite invite) {
        if (invite.getEmail() != null && !invite.getEmail().isBlank()) {
            return invite.getEmail();
        }
        if (invite.getUserId() == null) {
            return null;
        }
        return userLookup.contactOf(invite.getUserId()).email();
    }

    private String joinUrl(Meeting meeting, MeetingInvite invite) {
        return baseUrl + "/meet/" + meeting.getCode() + "?invite=" + invite.getInviteToken();
    }

    private String whenLine(Meeting meeting) {
        if (meeting.getScheduledAt() == null) {
            return "Starting now";
        }
        ZoneId zone = meeting.getOrganizerTimezone() == null
                ? ZoneId.of("UTC")
                : ZoneId.of(meeting.getOrganizerTimezone());
        return WHEN.format(meeting.getScheduledAt().atZone(zone)) + " (" + zone.getId() + ")";
    }
}
