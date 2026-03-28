package org.margin.server.social.space.repositories;

import org.margin.server.social.space.models.Space;
import org.margin.server.social.space.models.SpaceMember;
import org.margin.server.users.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpaceMemberRepository extends JpaRepository<SpaceMember, Long> {
    @Query("SELECT COUNT(sm) > 0 FROM SpaceMember sm WHERE sm.user.id = :userId AND sm.space.id = :spaceId")
    boolean existsSpaceMemberByUserAndSpace(Long userId, Long spaceId);
    
    List<SpaceMember> findByUser(User user);

    List<SpaceMember> findSpaceMemberBySpace(Space space);

    Optional<SpaceMember> findByUser_IdAndSpace_Id(Long userId, Long spaceId);

    @Query("SELECT sm.user FROM Channel c JOIN c.space s JOIN s.members sm WHERE c.id = :channelId")
    List<User> findSpaceMemberByChannel_Id(Long channelId);

    @Query("""
            SELECT COUNT(sm) > 0
            FROM SpaceMember sm
            WHERE sm.space.id =
                (SELECT c.space.id
                 FROM Channel c
                 WHERE c.id = :channelId)
            AND sm.user.id = :userId
            """)
    boolean existsByChannelIdAndUserId(Long userId, Long channelId);
}
