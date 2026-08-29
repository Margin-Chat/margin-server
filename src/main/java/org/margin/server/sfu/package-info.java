@ApplicationModule(allowedDependencies = {
        "presence",
        "shared",
        "social::api",
        "subscriptions::api",
        "users::api"
})
package org.margin.server.sfu;

import org.springframework.modulith.ApplicationModule;
