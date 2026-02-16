package org.margin.server.social.margin;

import org.margin.server.social.margin.models.Margin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MarginRepository extends JpaRepository<Margin, Long> {
    boolean existsMarginByName(String name);

    List<Margin> findByName(String name);
}
