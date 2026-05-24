package org.margin.server.sfu.models;

public record SfuJoinResponse(String sfuUrl, String roomToken, Integer maxVideoHeight) {
}
