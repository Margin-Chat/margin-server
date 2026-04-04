package org.margin.server.integrationtest.utils;

import org.margin.server.users.models.User;
import org.margin.server.users.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class UserUtils {
    private static UserRepository userRepository;

    @Autowired
    public UserUtils(UserRepository userRepository) {
        UserUtils.userRepository = userRepository;
    }

    public static User createUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setDisplayName(username);
        user.setPassword("hashed-password");
        user.setCreatedAt(Instant.now());
        return userRepository.save(user);
    }


}
