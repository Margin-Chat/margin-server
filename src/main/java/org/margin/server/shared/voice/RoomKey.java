package org.margin.server.shared.voice;

public sealed interface RoomKey {

    String MEETING_PREFIX = "m_";

    String value();

    record ChannelRoom(Long channelId) implements RoomKey {
        @Override
        public String value() {
            return String.valueOf(channelId);
        }
    }

    record MeetingRoom(String code) implements RoomKey {
        @Override
        public String value() {
            return MEETING_PREFIX + code;
        }
    }

    static RoomKey parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Room id must not be blank");
        }
        if (raw.startsWith(MEETING_PREFIX)) {
            String code = raw.substring(MEETING_PREFIX.length());
            if (code.isBlank()) {
                throw new IllegalArgumentException("Meeting room id must carry a code: " + raw);
            }
            return new MeetingRoom(code);
        }
        try {
            return new ChannelRoom(Long.parseLong(raw));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unrecognised room id: " + raw);
        }
    }
}
