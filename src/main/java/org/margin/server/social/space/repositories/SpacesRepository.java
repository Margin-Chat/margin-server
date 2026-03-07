package org.margin.server.social.space.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.margin.server.social.space.models.Space;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpacesRepository extends JpaRepository<Space, Long> {
    @Query("SELECT sp FROM Space sp")
    List<Space> getSpaces();

    @Query("SELECT sp FROM Space sp WHERE sp.name = :spaceName AND sp.margin.id = :marginId")
    Optional<Space> getSpaceByName(String spaceName, Long marginId);

    @Query("""
            SELECT sp
            FROM Space sp
            JOIN SpaceMember spm
                ON spm.user.id = :userId
            WHERE spm MEMBER OF sp.members
    """)
    List<Space> findByUser(Long userId);

    List<Space> findByMargin_Id(Long marginId);
}
