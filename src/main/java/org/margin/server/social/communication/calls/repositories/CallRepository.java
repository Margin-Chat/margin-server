package org.margin.server.social.communication.calls.repositories;

import org.margin.server.social.communication.calls.models.Call;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CallRepository extends JpaRepository<Call, Long> {
    @Query("SELECT c FROM Call c WHERE c.id = :callId")
    Call getCallByById(Long callId);
}
