@ApplicationModule(allowedDependencies = {
        "presence",
        "shared",
        "social::api",
        "social::events",
        "subscriptions::api",
        "users::api"
})
package org.margin.server.storage;

import org.springframework.modulith.ApplicationModule;
