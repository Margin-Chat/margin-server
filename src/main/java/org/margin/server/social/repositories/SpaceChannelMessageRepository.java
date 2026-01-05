package org.margin.server.social.repositories;

import org.margin.server.social.models.SpaceChannelMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpaceChannelMessageRepository extends JpaRepository<SpaceChannelMessage, Long> {

}
