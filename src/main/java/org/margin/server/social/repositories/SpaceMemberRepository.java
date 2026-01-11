package org.margin.server.social.repositories;

import org.margin.server.social.models.space.SpaceMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpaceMemberRepository extends JpaRepository<SpaceMember, Long> {
}
