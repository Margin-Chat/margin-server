package org.margin.server.social.api;

import org.springframework.web.multipart.MultipartFile;

public interface MarginIconStore {

    String save(MultipartFile icon);
}
