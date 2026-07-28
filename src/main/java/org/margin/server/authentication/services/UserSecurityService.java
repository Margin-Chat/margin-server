package org.margin.server.authentication.services;

import org.margin.server.authentication.entities.UserSecurity;
import org.margin.server.authentication.repositories.UserSecurityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSecurityService {

    private final UserSecurityRepository userSecurityRepository;

    public UserSecurityService(UserSecurityRepository userSecurityRepository) {
        this.userSecurityRepository = userSecurityRepository;
    }

    @Transactional(readOnly = true)
    public UserSecurity get(Long userId) {
        return userSecurityRepository.findById(userId).orElseGet(() -> new UserSecurity(userId));
    }

    @Transactional
    public UserSecurity save(UserSecurity security) {
        return userSecurityRepository.save(security);
    }

    @Transactional
    public int bumpTokenVersion(Long userId) {
        UserSecurity security = get(userId);
        security.setTokenVersion(security.getTokenVersion() + 1);
        return userSecurityRepository.save(security).getTokenVersion();
    }
}
