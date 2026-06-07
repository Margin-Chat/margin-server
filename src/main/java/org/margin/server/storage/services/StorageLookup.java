package org.margin.server.storage.services;

import org.margin.server.storage.dtos.StoredFileDTO;

import java.util.List;
import java.util.Map;

public interface StorageLookup {
    Map<Long, List<StoredFileDTO>> findAttachmentsByMessageIds(List<Long> messageIds);
}