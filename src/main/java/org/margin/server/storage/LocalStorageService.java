package org.margin.server.storage;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final StorageProperties storageProperties;

    public LocalStorageService(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @Override
    public String saveProfilePicture(MultipartFile file) {
        try {
            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path uploadPath = Paths.get(storageProperties.getLocal().getUploadDir());

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            return storageProperties.getLocal().getBaseUrl() + "/user-profiles/" + fileName;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save profile picture locally", e);
        }
    }

    @Override
    public String saveMarginIcon(MultipartFile file) {
        try {
            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path uploadPath = Paths.get(storageProperties.getLocal().getUploadDir());

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            return storageProperties.getLocal().getBaseUrl() + "/margin-icons/" + fileName;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save margin icon locally", e);
        }
    }

    @Override
    public void deleteProfilePicture(String url) {
        try {
            String fileName = url.substring(url.lastIndexOf('/') + 1);
            Path filePath = Paths.get(storageProperties.getLocal().getUploadDir()).resolve(fileName);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file", e);
        }
    }

    @Override
    public Resource getProfilePicture(String url) throws IOException {
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        Path filePath = Paths.get(storageProperties.getLocal().getUploadDir()).resolve(fileName);

        Resource resource = new UrlResource(filePath.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            throw new NoSuchFileException("File not found: " + fileName);
        }

        return resource;
    }
}