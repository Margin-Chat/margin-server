package org.margin.server.storage.services;

import org.margin.server.social.api.MarginIconStore;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class MarginIconStoreAdapter implements MarginIconStore {

    private final StorageService storageService;

    public MarginIconStoreAdapter(StorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public String save(MultipartFile icon) {
        return storageService.saveMarginIcon(icon);
    }
}
