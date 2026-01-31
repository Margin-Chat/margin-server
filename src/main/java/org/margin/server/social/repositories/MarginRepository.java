package org.margin.server.social.repositories;

import org.margin.server.social.models.margin.Margin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MarginRepository extends JpaRepository<Margin, Long> {
}
