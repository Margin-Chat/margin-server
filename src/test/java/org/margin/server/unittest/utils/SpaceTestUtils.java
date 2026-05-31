package org.margin.server.unittest.utils;

import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.space.models.Space;

public class SpaceTestUtils {

    public static Space createSpace(Long id) {
        Space space = new Space();
        space.setId(id);
        return space;
    }

    public static Space createSpace(Long id, Margin margin) {
        Space space = createSpace(id);
        space.setMargin(margin);
        return space;
    }
}
