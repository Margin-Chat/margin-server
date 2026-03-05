package org.margin.server.social.margin.repositories;

import org.margin.server.social.margin.models.MarginMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MarginMemberRepository extends JpaRepository<MarginMember, Long> {
    @Query("""
    SELECT mm
    FROM MarginMember mm
    WHERE mm.user.id = :userId
    """)
    List<MarginMember> findMarginMembersByUser(Long userId);
}
