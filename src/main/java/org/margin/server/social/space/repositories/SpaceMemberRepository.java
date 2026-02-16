package org.margin.server.social.space.repositories;

import org.margin.server.social.space.models.SpaceMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpaceMemberRepository extends JpaRepository<SpaceMember, Long> {
}
