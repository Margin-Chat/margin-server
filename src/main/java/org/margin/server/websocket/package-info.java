@ApplicationModule(allowedDependencies = {
        "authentication::api",
        "authentication::events",
        "notifications",
        "notifications::events",
        "presence",
        "presence::events",
        "sfu::api",
        "sfu::events",
        "sfu::models",
        "shared",
        "social::api",
        "social::calls",
        "social::conversation-dtos",
        "social::events",
        "subscriptions::api",
        "subscriptions::events",
        "users::api"
})
package org.margin.server.websocket;

import org.springframework.modulith.ApplicationModule;
