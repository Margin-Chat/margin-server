package org.margin.server.social.margin;

import org.springframework.modulith.NamedInterface;

import org.margin.server.social.margin.entities.Margin;

@NamedInterface("api")
public interface MarginLookup {
    Margin findByIconFileName(String fileName);

    Margin getById(Long marginId);
}