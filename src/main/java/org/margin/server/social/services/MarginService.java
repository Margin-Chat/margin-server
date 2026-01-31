package org.margin.server.social.services;

import org.margin.server.social.models.margin.Margin;
import org.margin.server.social.repositories.MarginRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

@Service
public class MarginService {

    private final MarginRepository marginRepository;

    public MarginService(MarginRepository marginRepository) {
        this.marginRepository = marginRepository;
    }

    public Margin getMargin(Long marginId) {
        return marginRepository.findById(marginId).orElseThrow();
    }
}
