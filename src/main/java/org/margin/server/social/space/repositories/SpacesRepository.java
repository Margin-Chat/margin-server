package org.margin.server.social.space.repositories;

import org.margin.server.social.models.Visibility;

import org.margin.server.social.space.models.Space;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
                    AND sp.margin.id = :marginId
            """)
    List<Space> findByUserAndMargin(Long userId, Long marginId);

    List<Space> findByMargin_Id(Long marginId);

    @Query("SELECT s FROM Space s WHERE s.margin.id = :marginId AND s.visibility = :visibility")
    List<Space> findByMarginIdAndVisibility(Long marginId, Visibility visibility);

    @Query("""
            SELECT sp FROM Space sp
            WHERE sp.margin.id = :marginId
            AND (
                sp.visibility = org.margin.server.social.models.Visibility.PUBLIC
                OR EXISTS (
                    SELECT sm FROM SpaceMember sm
                    WHERE sm.space = sp AND sm.user.id = :userId
                )
            )
            """)
    List<Space> findVisibleSpacesForUser(Long userId, Long marginId);
}
