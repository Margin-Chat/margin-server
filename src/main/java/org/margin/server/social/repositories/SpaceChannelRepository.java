package org.margin.server.social.repositories;

import org.margin.server.social.models.SpaceChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpaceChannelRepository extends JpaRepository<SpaceChannel, Long> {
    @Query("SELECT sc FROM SpaceChannel sc WHERE sc.spaceId = :spaceId")
    List<SpaceChannel> getSpaceChannelsBySpaceId(Long spaceId);
}
