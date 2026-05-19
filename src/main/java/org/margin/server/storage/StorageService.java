package org.margin.server.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

public interface StorageService {
    String saveProfilePicture(MultipartFile file);

    String saveMarginIcon(MultipartFile file);

    String saveStoredFile(MultipartFile file);

    void deleteProfilePicture(String url);

    void delete(String url);

    Resource getFile(String url) throws IOException;

    default Optional<String> presign(String url) {
        return presign(url, null);
    }
    
    default Optional<String> presign(String url, String downloadFilename) {
        return Optional.empty();
    }
}
