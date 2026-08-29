@ApplicationModule(allowedDependencies = {
        "email",
        "shared",
        "social::api",
        "users::api"
})
package org.margin.server.subscriptions;

import org.springframework.modulith.ApplicationModule;
