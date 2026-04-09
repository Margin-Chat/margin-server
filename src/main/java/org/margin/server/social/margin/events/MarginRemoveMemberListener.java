package org.margin.server.social.margin.events;

import org.margin.server.social.margin.service.MarginService;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class MarginRemoveMemberListener implements ApplicationListener<RemoveUserFromMarginEvent> {
    private final MarginService marginService;

    public MarginRemoveMemberListener(MarginService marginService) {
        this.marginService = marginService;
    }

    @Override
    public void onApplicationEvent(RemoveUserFromMarginEvent event) {
        marginService.removeMarginMember(event.getMarginId(), event.getUserId());
    }
}
