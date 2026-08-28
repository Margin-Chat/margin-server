package org.margin.server.meetings.services;

import java.util.Base64;
import java.util.UUID;

final class MeetingCodes {

    private MeetingCodes() {
    }

    static String newCode() {
        UUID uuid = UUID.randomUUID();
        byte[] bytes = new byte[16];
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (uuid.getMostSignificantBits() >>> (8 * (7 - i)));
            bytes[8 + i] = (byte) (uuid.getLeastSignificantBits() >>> (8 * (7 - i)));
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
