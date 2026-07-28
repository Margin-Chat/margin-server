package org.margin.server.social.margin.listeners;

import org.margin.server.social.margin.entities.MarginMember;
import org.margin.server.social.margin.repositories.MarginMemberRepository;
import org.margin.server.social.margin.service.MarginService;
import org.margin.server.users.events.UserDeletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserDeletedListener {

    private final MarginService marginService;
    private final MarginMemberRepository marginMemberRepository;

    public UserDeletedListener(MarginService marginService,
                               MarginMemberRepository marginMemberRepository) {
        this.marginService = marginService;
        this.marginMemberRepository = marginMemberRepository;
    }

    @EventListener
    public void onUserDeleted(UserDeletedEvent event) {
        List<Long> marginIds = marginMemberRepository.findMarginMembersByUser(event.userId()).stream()
                .map(MarginMember::getMargin)
                .map(margin -> margin.getId())
                .toList();

        marginIds.forEach(marginId -> marginService.removeMarginMember(marginId, event.userId()));
    }
}
