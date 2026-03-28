package org.margin.server.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface StorageService {
    String saveProfilePicture(MultipartFile file);

    String saveMarginIcon(MultipartFile file);

    void deleteProfilePicture(String url);

    Resource getFile(String url) throws IOException;
}