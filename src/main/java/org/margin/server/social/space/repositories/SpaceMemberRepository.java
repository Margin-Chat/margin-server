package org.margin.server.social.space.repositories;

import org.margin.server.social.space.models.Space;
import org.margin.server.social.space.models.SpaceMember;
import org.margin.server.users.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpaceMemberRepository extends JpaRepository<SpaceMember, Long> {
    boolean existsSpaceMemberByUserAndSpace(User user, Space space);

    List<SpaceMember> findByUser(User user);

    List<SpaceMember> findSpaceMemberBySpace(Space space);
}
