package org.margin.server.users.api;

import org.springframework.web.multipart.MultipartFile;

public interface ProfilePictureStore {

    String save(MultipartFile file);

    void delete(String storedUrl);
}
