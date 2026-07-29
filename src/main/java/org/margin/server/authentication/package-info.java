@ApplicationModule(allowedDependencies = {
        "email",
        "shared",
        "users::api",
        "users::identity"
})
package org.margin.server.authentication;

import org.springframework.modulith.ApplicationModule;
