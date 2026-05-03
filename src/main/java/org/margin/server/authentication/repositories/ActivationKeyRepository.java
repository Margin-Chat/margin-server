package org.margin.server.authentication.repositories;

import org.margin.server.authentication.entities.ActivationKey;
import org.margin.server.users.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ActivationKeyRepository extends JpaRepository<ActivationKey, String> {
    Optional<ActivationKey> findActivationKeyByToken(String token);

    Optional<ActivationKey> findActivationKeyByUser(User user);
}
