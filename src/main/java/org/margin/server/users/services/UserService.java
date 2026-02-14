package org.margin.server.users.services;

import org.margin.server.users.models.dtos.UserDTO;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.margin.server.users.models.User;
import org.margin.server.users.repositories.UserRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {
	private final UserRepository userRepository;
    private final UserCacheService userCacheService;

    public UserService(UserRepository userRepository, UserCacheService userCacheService) {
		this.userRepository = userRepository;
        this.userCacheService = userCacheService;
    }

    public User getById(Long id) {
        return userCacheService.getById(id);
    }

    public User getByUsername(String username) {
        return userRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("User not found"));
    }

    public void savePublicPrivateKeysForUser(Long userId, String publicKey, String encryptedPrivateKey) {
        User user = getById(userId);
        user.getEncryption().setPublicKey(publicKey);
        user.getEncryption().setEncryptedPrivateKey(encryptedPrivateKey);
        userRepository.save(user);
    }

    public List<UserDTO> searchForUser(String query) {
        return userRepository.findTop20ByUsernameContainingIgnoreCase(query).stream()
                .map(UserDTO::new)
                .collect(Collectors.toList());
    }

    public void evictUserCache(Long userId) {
        userCacheService.evictUserCache(userId);
    }
}
