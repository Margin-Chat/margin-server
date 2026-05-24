package org.margin.server.storage.repositories;

import org.margin.server.storage.models.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    @Query("""
            SELECT f FROM StoredFile f
            LEFT JOIN FETCH f.channel c
            LEFT JOIN FETCH c.conversation
            WHERE f.storageUrl LIKE CONCAT('%', :suffix)
            """)
    Optional<StoredFile> findFirstByStorageUrlEndsWith(@Param("suffix") String suffix);

    @Query("""
            SELECT f FROM StoredFile f
            JOIN FETCH f.uploadedBy
            WHERE f.scope = org.margin.server.storage.models.StoredFileScope.MARGIN
              AND f.margin.id = :marginId
              AND f.inline = false
            ORDER BY f.uploadedAt DESC
            """)
    List<StoredFile> findMarginFiles(@Param("marginId") Long marginId);

    @Query("""
            SELECT f FROM StoredFile f
            JOIN FETCH f.uploadedBy
            WHERE f.scope = org.margin.server.storage.models.StoredFileScope.CHANNEL
              AND f.channel.id = :channelId
              AND f.inline = false
            ORDER BY f.uploadedAt DESC
            """)
    List<StoredFile> findChannelFiles(@Param("channelId") Long channelId);

    @Query("""
            SELECT f FROM StoredFile f
            JOIN FETCH f.uploadedBy
            WHERE f.messageId IN :messageIds
            """)
    List<StoredFile> findByMessageIds(@Param("messageIds") List<Long> messageIds);

    @Query("""
            SELECT COALESCE(SUM(f.sizeBytes), 0)
            FROM StoredFile f
            WHERE f.margin.id = :marginId
            """)
    long sumSizeBytesByMargin(@Param("marginId") Long marginId);
}
