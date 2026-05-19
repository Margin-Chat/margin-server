package org.margin.server.social.margin;

import org.margin.server.social.margin.entities.Margin;

public interface MarginLookup {
    Margin findByIconFileName(String fileName);

    Margin getById(Long marginId);
}