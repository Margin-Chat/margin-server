package org.margin.server.social.margin.service;

import org.margin.server.social.margin.MarginLookup;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.exceptions.MarginNotFoundException;
import org.margin.server.social.margin.repositories.MarginRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
public class MarginLookupService implements MarginLookup {

    private final MarginRepository marginRepository;

    public MarginLookupService(MarginRepository marginRepository) {
        this.marginRepository = marginRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Margin getById(Long marginId) {
        return marginRepository.findById(marginId)
                .orElseThrow(() -> new MarginNotFoundException(marginId));
    }

    @Override
    @Transactional(readOnly = true)
    public Margin findByIconFileName(String fileName) {
        return marginRepository.findFirstByIconUrlEndsWith("/margin-icons/" + fileName)
                .orElseThrow(() -> new MarginNotFoundException(fileName));
    }
}
