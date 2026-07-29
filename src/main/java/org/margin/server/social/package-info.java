@ApplicationModule(allowedDependencies = {
        "presence",
        "shared",
        "users::api",
        "users::events"
})
package org.margin.server.social;

import org.springframework.modulith.ApplicationModule;
