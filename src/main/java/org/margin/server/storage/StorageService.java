package org.margin.server.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    String saveProfilePicture(MultipartFile file);
    String saveMarginIcon(MultipartFile file);
    void deleteProfilePicture(String url);
}