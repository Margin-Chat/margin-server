package org.margin.server.authentication.repositories;

import org.margin.server.authentication.entities.UserSecurity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSecurityRepository extends JpaRepository<UserSecurity, Long> {
}
