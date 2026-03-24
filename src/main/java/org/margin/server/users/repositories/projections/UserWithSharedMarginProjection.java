package org.margin.server.users.repositories.projections;

import org.margin.server.users.models.User;

public record UserWithSharedMarginProjection(User user, String marginName) {
}
