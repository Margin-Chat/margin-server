package org.margin.server.unittest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.margin.server.shared.voice.RoomKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomKeyTest {

    @Test
    @DisplayName("channel rooms keep the bare numeric encoding the SFU and clients already use")
    void channelRoom_EncodesAsBareNumber() {
        assertThat(new RoomKey.ChannelRoom(12345L).value()).isEqualTo("12345");
    }

    @Test
    @DisplayName("meeting rooms carry a prefix")
    void meetingRoom_EncodesWithPrefix() {
        assertThat(new RoomKey.MeetingRoom("abc").value()).isEqualTo("m_abc");
    }

    @Test
    @DisplayName("a numeric room id parses back to the same channel")
    void parse_NumericRoundTrips() {
        assertThat(RoomKey.parse("12345")).isEqualTo(new RoomKey.ChannelRoom(12345L));
    }

    @Test
    @DisplayName("a prefixed room id parses back to the same meeting")
    void parse_MeetingRoundTrips() {
        assertThat(RoomKey.parse("m_abc")).isEqualTo(new RoomKey.MeetingRoom("abc"));
    }

    @Test
    @DisplayName("a meeting room id can never be read as a channel id")
    void parse_MeetingIdIsNeverAChannel() {
        assertThat(RoomKey.parse("m_123")).isInstanceOf(RoomKey.MeetingRoom.class);
    }

    @Test
    @DisplayName("meeting codes containing the prefix survive a round trip")
    void parse_CodeContainingPrefixRoundTrips() {
        RoomKey.MeetingRoom room = new RoomKey.MeetingRoom("m_nested");

        assertThat(RoomKey.parse(room.value())).isEqualTo(room);
    }

    @Test
    @DisplayName("unrecognised room ids are rejected rather than silently mis-parsed")
    void parse_RejectsGarbage() {
        assertThatThrownBy(() -> RoomKey.parse("banana"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a bare prefix with no code is rejected")
    void parse_RejectsPrefixWithoutCode() {
        assertThatThrownBy(() -> RoomKey.parse("m_"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("blank and null room ids are rejected")
    void parse_RejectsBlank() {
        assertThatThrownBy(() -> RoomKey.parse("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RoomKey.parse("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RoomKey.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
