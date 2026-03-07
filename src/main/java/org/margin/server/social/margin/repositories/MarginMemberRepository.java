package org.margin.server.social.margin.repositories;

import org.margin.server.social.margin.models.MarginMember;
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
}
