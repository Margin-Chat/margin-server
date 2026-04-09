package org.margin.server.social.margin.repositories;

import org.margin.server.social.margin.entities.MarginMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MarginMemberRepository extends JpaRepository<MarginMember, Long> {
    @Query("""
            SELECT mm
            FROM MarginMember mm
            WHERE mm.user.id = :userId
            """)
    List<MarginMember> findMarginMembersByUser(Long userId);

    Optional<MarginMember> findByUser_IdAndMargin_Id(Long userId, Long marginId);

    boolean existsByUser_IdAndMargin_Id(Long userId, Long marginId);

    @Query("""
            SELECT CASE WHEN COUNT(mm) > 0 THEN true ELSE false END
            FROM MarginMember mm
            WHERE mm.margin.id = :marginId
            AND mm.user.id = :userId
            """)
    boolean existsByMarginIdAndUserId(Long marginId, Long userId);

    List<MarginMember> findByMargin_Id(Long marginId);
}
