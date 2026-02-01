package org.margin.server.social.services;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.models.Visibility;
import org.margin.server.social.models.margin.Margin;
import org.margin.server.social.repositories.MarginRepository;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MarginService {

    private final MarginRepository marginRepository;

    public MarginService(MarginRepository marginRepository) {
        this.marginRepository = marginRepository;
    }

    public Margin getMargin(Long marginId) {
        return marginRepository.findById(marginId).orElseThrow();
    }

    public void createMargin(String name, String description, Visibility visibility) {
        Margin margin = new Margin();
        margin.setName(name);
        margin.setDescription(description);
        margin.setVisibility(visibility);
        marginRepository.save(margin);

      log.info("Created new Margin with id {}", margin.getId());
    }
}
