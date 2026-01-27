package org.margin.server.users.services;

import org.margin.server.users.models.UserDTO;
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

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

    @Cacheable(value = "users", key = "#id")
	public User getById(Long id) {
		return userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
	}

    public User getByUsername(String username) {
        return userRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("User not found"));
    }

    public void savePublicPrivateKeysForUser(Long userId, String publicKey, String encryptedPrivateKey) {
        User user = getById(userId);
        user.setPublicKey(publicKey);
        user.setEncryptedPrivateKey(encryptedPrivateKey);
        userRepository.save(user);
    }

    public List<UserDTO> searchForUser(String query) {
        return userRepository.findTop20ByUsernameContainingIgnoreCase(query).stream()
                .map(UserDTO::new)
                .collect(Collectors.toList());
    }

    @CacheEvict(value = "users", key = "#userId")
    public void evictUserCache(Long userId) {
    }
}
