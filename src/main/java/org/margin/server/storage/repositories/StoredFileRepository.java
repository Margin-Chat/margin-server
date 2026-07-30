package org.margin.server.storage.repositories;

import org.margin.server.storage.models.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    Optional<StoredFile> findFirstByStorageUrlEndsWith(String suffix);

    @Query("""
            SELECT f FROM StoredFile f
            WHERE f.scope = org.margin.server.storage.models.StoredFileScope.MARGIN
              AND f.marginId = :marginId
              AND f.inline = false
            ORDER BY f.uploadedAt DESC
            """)
    List<StoredFile> findMarginFiles(@Param("marginId") Long marginId);

    @Query("""
            SELECT f FROM StoredFile f
            WHERE f.scope = org.margin.server.storage.models.StoredFileScope.CHANNEL
              AND f.channelId = :channelId
              AND f.inline = false
            ORDER BY f.uploadedAt DESC
            """)
    List<StoredFile> findChannelFiles(@Param("channelId") Long channelId);

    List<StoredFile> findByMessageIdIn(List<Long> messageIds);

    @Query("""
            SELECT COALESCE(SUM(f.sizeBytes), 0)
            FROM StoredFile f
            WHERE f.marginId = :marginId
            """)
    long sumSizeBytesByMargin(@Param("marginId") Long marginId);
}
