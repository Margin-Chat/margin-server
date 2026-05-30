package org.margin.server.unittest.utils;

import org.margin.server.users.models.User;

public class UserTestUtils {

    public static User createUser(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    public static User createUser(Long id, String handle) {
        User user = new User();
        user.setId(id);
        user.setHandle(handle);
        return user;
    }
}
