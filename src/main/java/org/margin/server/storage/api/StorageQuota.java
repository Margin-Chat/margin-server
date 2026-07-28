package org.margin.server.storage.api;

public interface StorageQuota {

    long sumStoredBytesForMargin(Long marginId);
}
