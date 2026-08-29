package org.margin.server.social.api;

import org.springframework.web.multipart.MultipartFile;

public interface MarginIconCommands {

    String save(MultipartFile icon);
}
