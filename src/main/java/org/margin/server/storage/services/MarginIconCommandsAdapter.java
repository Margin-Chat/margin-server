package org.margin.server.storage.services;

import org.margin.server.social.api.MarginIconCommands;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class MarginIconCommandsAdapter implements MarginIconCommands {

    private final StorageService storageService;

    public MarginIconCommandsAdapter(StorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public String save(MultipartFile icon) {
        return storageService.saveMarginIcon(icon);
    }
}
