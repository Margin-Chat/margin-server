package org.margin.server.social.repositories;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.margin.server.social.models.DirectMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DirectChatMessageRepository extends JpaRepository<DirectMessage, Long> {
	@Query("""
			SELECT dm
			FROM DirectMessage dm
			WHERE dm.fromUserId = :fromUserId
			  AND dm.toUserId = :toUserId
			ORDER BY dm.createdAt ASC
			""")
	List<DirectMessage> findByFromUserIdAndToUserId(@Param("fromUserId") Long fromUserId,
                                                    @Param("toUserId") Long toUserId);
}
