package org.margin.server.social.margin;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.models.Visibility;
import org.margin.server.social.margin.models.Margin;
import org.margin.server.storage.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class MarginService {

    private final MarginRepository marginRepository;
    private final StorageService storageService;

    public MarginService(MarginRepository marginRepository, StorageService storageService) {
        this.marginRepository = marginRepository;
        this.storageService = storageService;
    }

    public Margin getMargin(Long marginId) {
        return marginRepository.findById(marginId).orElseThrow();
    }

    public void createMargin(String name, String description, Visibility visibility, MultipartFile marginIcon) {
        String marginIconUrl = null;

        if (marginIcon != null && !marginIcon.isEmpty()) {
            marginIconUrl = storageService.saveMarginIcon(marginIcon);
        }

        Margin margin = new Margin();
        margin.setName(name);
        margin.setDescription(description);
        margin.setVisibility(visibility);
        margin.setMarginIconUrl(marginIconUrl);
        marginRepository.save(margin);

      log.info("Created new Margin with id {}", margin.getId());
    }
}
