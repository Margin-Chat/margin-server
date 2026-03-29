package org.margin.server.authentication.repositories;

import org.margin.server.authentication.entities.BetaKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface BetaKeyRepository extends JpaRepository<BetaKey, Long> {
    @Query("SELECT b FROM BetaKey b WHERE b.key = :key")
    Optional<BetaKey> findByKey(String key);
}