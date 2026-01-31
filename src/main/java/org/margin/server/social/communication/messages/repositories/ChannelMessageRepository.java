package org.margin.server.social.communication.messages.repositories;

import org.margin.server.social.communication.messages.models.ChannelMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChannelMessageRepository extends JpaRepository<ChannelMessage, Long> {
    @Query("SELECT spmsg FROM ChannelMessage spmsg WHERE spmsg.channel.id = :channelId")
    List<ChannelMessage> getChannelMessageByChannelId(Long channelId);
}
