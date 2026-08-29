@ApplicationModule(allowedDependencies = {
        "email",
        "email::events",
        "shared",
        "users::api",
        "users::events",
        "users::identity"
})
package org.margin.server.authentication;

import org.springframework.modulith.ApplicationModule;
