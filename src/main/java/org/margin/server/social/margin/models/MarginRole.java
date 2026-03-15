package org.margin.server.social.margin.models;

import lombok.Getter;

@Getter
public enum MarginRole {
    OWNER(1),
    ADMIN(2),
    MEMBER(3);

    private final int rank;
    MarginRole(int rank) { this.rank = rank; }

}

