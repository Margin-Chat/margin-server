package org.margin.server.unittest.utils;

import org.margin.server.social.margin.entities.Margin;

public class MarginTestUtils {

    public static Margin createMargin(Long id, String name) {
        Margin margin = new Margin();
        margin.setId(id);
        margin.setName(name);
        return margin;
    }
}
