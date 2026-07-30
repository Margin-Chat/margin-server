package org.margin.server.shared.authorization;

public interface MarginAccessChecker {

    void requireMarginMember(Long userId, Long marginId);

    void requireMarginAdmin(Long userId, Long marginId);

    void requireMarginOwner(Long userId, Long marginId);

    void requireSpaceAdmin(Long userId, Long spaceId);

    void requireSpaceMember(Long userId, Long spaceId);

    void requireChannelMember(Long userId, Long channelId);
}
