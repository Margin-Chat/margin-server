@ApplicationModule(allowedDependencies = {
        "authentication::filters",
        "meetings::filters"
})
package org.margin.server.config;

import org.springframework.modulith.ApplicationModule;
