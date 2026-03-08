package org.margin.server.social.channel.repositories;

import org.margin.server.social.channel.channel.Channel;
import org.margin.server.social.space.models.Space;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChannelRepository extends JpaRepository<Channel, Long> {
    @Query("SELECT sc FROM Channel sc WHERE sc.space.id = :spaceId")
    List<Channel> getChannelsBySpaceId(Long spaceId);

    List<Channel> findChannelBySpace(Space space);
}
