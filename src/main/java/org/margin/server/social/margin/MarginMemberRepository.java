package org.margin.server.social.margin;

import org.margin.server.social.margin.models.MarginMember;
import org.margin.server.users.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

interface MarginMemberRepository extends JpaRepository<MarginMember, Long> {
    @Query("""
    SELECT mm
    FROM MarginMember mm
    WHERE mm.user.id = :userId
    """)
    List<MarginMember> findMarginMembersByUser(Long userId);
}
