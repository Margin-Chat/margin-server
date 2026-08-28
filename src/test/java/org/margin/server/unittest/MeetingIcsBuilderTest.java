package org.margin.server.unittest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.margin.server.meetings.entities.Meeting;
import org.margin.server.meetings.services.MeetingIcsBuilder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MeetingIcsBuilderTest {

    private final MeetingIcsBuilder builder = new MeetingIcsBuilder();

    private Meeting meeting(String title, int sequence) {
        Meeting meeting = new Meeting();
        meeting.setCode("abc123");
        meeting.setTitle(title);
        meeting.setScheduledAt(Instant.parse("2026-09-02T13:00:00Z"));
        meeting.setDurationMinutes(30);
        meeting.setIcsSequence(sequence);
        return meeting;
    }

    @Test
    @DisplayName("the uid stays with the meeting so a later update replaces the same entry")
    void uidIsStableForTheMeeting() {
        String first = builder.build(meeting("Sync", 0), "https://margin.chat/app/#/meet/abc123", false);
        String second = builder.build(meeting("Sync moved", 1), "https://margin.chat/app/#/meet/abc123", false);

        assertThat(first).contains("UID:abc123@margin.chat");
        assertThat(second).contains("UID:abc123@margin.chat");
    }

    @Test
    @DisplayName("the sequence rises with each change, which is what makes clients treat it as an edit")
    void sequenceTracksTheMeeting() {
        assertThat(builder.build(meeting("Sync", 0), "https://x/1", false)).contains("SEQUENCE:0");
        assertThat(builder.build(meeting("Sync", 3), "https://x/1", false)).contains("SEQUENCE:3");
    }

    @Test
    @DisplayName("an invite is a REQUEST and a cancellation is a CANCEL")
    void methodReflectsTheIntent() {
        assertThat(builder.build(meeting("Sync", 0), "https://x/1", false)).contains("METHOD:REQUEST");
        assertThat(builder.build(meeting("Sync", 1), "https://x/1", true)).contains("METHOD:CANCEL");
    }

    @Test
    @DisplayName("start and end come from the scheduled time and duration")
    void timesComeFromTheMeeting() {
        String ics = builder.build(meeting("Sync", 0), "https://x/1", false);

        assertThat(ics).contains("DTSTART:20260902T130000Z");
        assertThat(ics).contains("DTEND:20260902T133000Z");
    }

    @Test
    @DisplayName("a title with ics punctuation is escaped rather than breaking the file")
    void punctuationInTheTitleIsEscaped() {
        String ics = builder.build(meeting("Budget, Q4; planning", 0), "https://x/1", false);

        assertThat(ics).contains("Budget\\, Q4\\; planning");
    }

    @Test
    @DisplayName("a meeting with no duration still gets an end time")
    void missingDurationFallsBack() {
        Meeting meeting = meeting("Sync", 0);
        meeting.setDurationMinutes(null);

        assertThat(builder.build(meeting, "https://x/1", false)).contains("DTEND:20260902T140000Z");
    }
}
