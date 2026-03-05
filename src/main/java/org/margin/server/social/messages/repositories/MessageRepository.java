package org.margin.server.social.messages.repositories;

import org.margin.server.social.messages.models.Message;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    @Query("""
            SELECT m FROM Message m
            JOIN FETCH m.conversation c
            JOIN FETCH m.fromUser u
            WHERE m.conversation.id = :conversationId
            ORDER BY m.createdAt DESC
            """)
    List<Message> findRecentMessages(@Param("conversationId") Long conversationId, Pageable pageable);}
