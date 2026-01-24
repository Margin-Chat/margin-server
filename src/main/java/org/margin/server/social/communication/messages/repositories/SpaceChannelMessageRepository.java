package org.margin.server.social.communication.messages.repositories;

import org.margin.server.social.models.SpaceChannelMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpaceChannelMessageRepository extends JpaRepository<SpaceChannelMessage, Long> {
    @Query("SELECT spmsg FROM SpaceChannelMessage spmsg WHERE spmsg.channelId = :channelId")
    List<SpaceChannelMessage> getSpaceChannelMessageByChannelId(Long channelId);
}
