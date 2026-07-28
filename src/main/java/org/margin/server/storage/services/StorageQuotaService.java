package org.margin.server.storage.services;

import org.margin.server.storage.api.StorageQuota;
import org.margin.server.storage.repositories.StoredFileRepository;
import org.springframework.stereotype.Service;

@Service
public class StorageQuotaService implements StorageQuota {

    private final StoredFileRepository storedFileRepository;

    public StorageQuotaService(StoredFileRepository storedFileRepository) {
        this.storedFileRepository = storedFileRepository;
    }

    @Override
    public long sumStoredBytesForMargin(Long marginId) {
        return storedFileRepository.sumSizeBytesByMargin(marginId);
    }
}
