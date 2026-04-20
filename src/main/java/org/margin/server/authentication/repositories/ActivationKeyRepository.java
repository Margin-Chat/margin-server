package org.margin.server.authentication.repositories;

import org.margin.server.authentication.entities.ActivationKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivationKeyRepository extends JpaRepository<ActivationKey, String> {
}
