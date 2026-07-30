package org.margin.server.storage.services;

import org.margin.server.users.api.ProfilePictureCommands;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ProfilePictureCommandsAdapter implements ProfilePictureCommands {

    private final StorageService storageService;

    public ProfilePictureCommandsAdapter(StorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public String save(MultipartFile file) {
        return storageService.saveProfilePicture(file);
    }

    @Override
    public void delete(String storedUrl) {
        storageService.deleteProfilePicture(storedUrl);
    }
}
