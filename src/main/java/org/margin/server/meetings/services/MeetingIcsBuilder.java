package org.margin.server.meetings.services;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStamp;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.immutable.ImmutableCalScale;
import net.fortuna.ical4j.model.property.immutable.ImmutableMethod;
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion;
import net.fortuna.ical4j.model.property.Sequence;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Url;
import org.margin.server.meetings.entities.Meeting;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

@Component
public class MeetingIcsBuilder {

    private static final Duration DEFAULT_LENGTH = Duration.ofHours(1);

    public String build(Meeting meeting, String joinUrl, boolean cancelled) {
        Instant start = meeting.getScheduledAt() == null ? Instant.now() : meeting.getScheduledAt();
        Duration length = meeting.getDurationMinutes() == null
                ? DEFAULT_LENGTH
                : Duration.ofMinutes(meeting.getDurationMinutes());

        VEvent event = new VEvent();
        event.add(new Uid(meeting.getCode() + "@margin.chat"));
        event.add(new DtStamp());
        event.add(new DtStart<>(start));
        event.add(new DtEnd<>(start.plus(length)));
        event.add(new Summary(meeting.getTitle() == null ? "Meeting" : meeting.getTitle()));
        event.add(new Sequence(meeting.getIcsSequence()));
        event.add(new Url(java.net.URI.create(joinUrl)));

        Calendar calendar = new Calendar();
        calendar.add(new ProdId("-//margin//meetings//EN"));
        calendar.add(ImmutableVersion.VERSION_2_0);
        calendar.add(ImmutableCalScale.GREGORIAN);
        calendar.add(cancelled ? ImmutableMethod.CANCEL : ImmutableMethod.REQUEST);
        calendar.add(event);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CalendarOutputter outputter = new CalendarOutputter(false);
            outputter.output(calendar, out);
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the calendar entry", e);
        }
    }
}
