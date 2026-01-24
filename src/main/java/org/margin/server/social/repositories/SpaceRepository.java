package org.margin.server.social.repositories;

import org.margin.server.users.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.margin.server.social.models.space.Space;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpaceRepository extends JpaRepository<Space, Long> {
    @Query("SELECT sp FROM Space sp")
    List<Space> getSpaces();

    @Query("SELECT spm.user FROM SpaceMember spm WHERE spm.id.spaceId = :spaceId")
    List<User> getUsersForSpace(Long spaceId);

    @Query("SELECT sp FROM Space sp WHERE sp.name = :spaceName")
    Optional<Space> getSpaceByName(String spaceName);
}
