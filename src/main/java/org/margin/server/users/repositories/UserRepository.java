package org.margin.server.users.repositories;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.margin.server.users.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
	@Query("""
			SELECT u
			FROM User u
			WHERE LOWER(u.username) = :username
			""")
	Optional<User> findByUsername(@Param("username") String username);

	List<User> findAllByIdIn(List<Long> userIds);

	@Query("""
       SELECT u
       FROM User u
       WHERE u.username LIKE :username
       """)
	List<User> findByUsernameLike(@Param("username") String query, Pageable pageable);

	List<User> findTop20ByUsernameContainingIgnoreCase(String username);

	Optional<User> findByEmail(String email);
}
