package org.margin.server.social.margin.service;

import org.margin.server.social.api.MarginDirectory;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.exceptions.MarginNotFoundException;
import org.margin.server.social.margin.models.MarginRole;
import org.margin.server.social.margin.repositories.MarginMemberRepository;
import org.margin.server.social.margin.repositories.MarginRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarginDirectoryService implements MarginDirectory {

    private final MarginRepository marginRepository;
    private final MarginMemberRepository marginMemberRepository;

    public MarginDirectoryService(MarginRepository marginRepository,
                                  MarginMemberRepository marginMemberRepository) {
        this.marginRepository = marginRepository;
        this.marginMemberRepository = marginMemberRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public MarginSummary summaryOf(Long marginId) {
        Margin margin = marginRepository.findById(marginId)
                .orElseThrow(() -> new MarginNotFoundException(marginId));

        return new MarginSummary(margin.getId(), margin.getName(), margin.getMembers().size());
    }

    @Override
    @Transactional(readOnly = true)
    public MarginIcon iconByFileName(String fileName) {
        Margin margin = marginRepository.findFirstByIconUrlEndsWith("/margin-icons/" + fileName)
                .orElseThrow(() -> new MarginNotFoundException(fileName));

        return new MarginIcon(margin.getId(), margin.getIconUrl());
    }

    @Override
    @Transactional(readOnly = true)
    public Long ownerUserIdOf(Long marginId) {
        return marginMemberRepository.findByMargin_IdAndRole(marginId, MarginRole.OWNER)
                .orElseThrow(() -> new IllegalStateException("Margin " + marginId + " has no owner"))
                .getUser()
                .getId();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> adminUserIdsOf(Long marginId) {
        return marginMemberRepository.findByMargin_Id(marginId).stream()
                .filter(m -> m.getRole() == MarginRole.OWNER || m.getRole() == MarginRole.ADMIN)
                .map(m -> m.getUser().getId())
                .toList();
    }
}
