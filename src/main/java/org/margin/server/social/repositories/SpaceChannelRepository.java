package org.margin.server.social.repositories;

import org.margin.server.social.models.SpaceChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpaceChannelRepository extends JpaRepository<SpaceChannel, Long> {
}
