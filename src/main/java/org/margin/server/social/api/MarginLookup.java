package org.margin.server.social.api;

public interface MarginLookup {

    MarginSummary summaryOf(Long marginId);

    MarginIcon iconByFileName(String fileName);

    Long ownerUserIdOf(Long marginId);

    java.util.List<Long> adminUserIdsOf(Long marginId);

    java.util.List<MarginSummary> marginsForUser(Long userId);

    record MarginSummary(Long id, String name, int memberCount) {
    }

    record MarginIcon(Long marginId, String iconUrl) {
    }
}
