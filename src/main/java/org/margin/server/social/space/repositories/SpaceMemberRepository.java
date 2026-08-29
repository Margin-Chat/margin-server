package org.margin.server.social.space.repositories;

import org.margin.server.social.space.models.Space;
import org.margin.server.social.space.models.SpaceMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpaceMemberRepository extends JpaRepository<SpaceMember, Long> {
    @Query("SELECT COUNT(sm) > 0 FROM SpaceMember sm WHERE sm.userId = :userId AND sm.space.id = :spaceId")
    boolean existsSpaceMemberByUserAndSpace(Long userId, Long spaceId);
    
    List<SpaceMember> findByUserId(Long userId);

    List<SpaceMember> findSpaceMemberBySpace(Space space);

    Optional<SpaceMember> findByUserIdAndSpaceId(Long userId, Long spaceId);

    @Query("SELECT sm.userId FROM Channel c JOIN c.space s JOIN s.members sm WHERE c.id = :channelId")
    List<Long> findUserIdsByChannelId(Long channelId);

    @Query("""
            SELECT COUNT(sm) > 0
            FROM SpaceMember sm
            WHERE sm.space.id =
                (SELECT c.space.id
                 FROM Channel c
                 WHERE c.id = :channelId)
            AND sm.userId = :userId
            """)
    boolean existsByChannelIdAndUserId(Long userId, Long channelId);
}
