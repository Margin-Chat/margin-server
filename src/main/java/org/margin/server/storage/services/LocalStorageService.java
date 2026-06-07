package org.margin.server.storage.services;

import org.jspecify.annotations.NonNull;
import org.margin.server.storage.StorageProperties;
import org.margin.server.storage.exceptions.StorageException;
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
            String fileName = getFileName(file);
            return storageProperties.getLocal().getBaseUrl() + "/user-profiles/" + fileName;
        } catch (IOException e) {
            throw new StorageException("Failed to save profile picture locally", e);
        }
    }

    @Override
    public String saveMarginIcon(MultipartFile file) {
        try {
            String fileName = getFileName(file);
            return storageProperties.getLocal().getBaseUrl() + "/margin-icons/" + fileName;
        } catch (IOException e) {
            throw new StorageException("Failed to save margin icon locally", e);
        }
    }

    @Override
    public String saveStoredFile(MultipartFile file) {
        try {
            String fileName = getFileName(file);
            return storageProperties.getLocal().getBaseUrl() + "/stored-files/" + fileName;
        } catch (IOException e) {
            throw new StorageException("Failed to save file locally", e);
        }
    }

    @Override
    public void deleteProfilePicture(String url) {
        delete(url);
    }

    @Override
    public void delete(String url) {
        try {
            Files.deleteIfExists(uploadDir().resolve(fileNameFromUrl(url)));
        } catch (IOException e) {
            throw new StorageException("Failed to delete file", e);
        }
    }

    @Override
    public Resource getFile(String url) throws IOException {
        String fileName = fileNameFromUrl(url);
        Path uploadDir = uploadDir();
        Path filePath = uploadDir.resolve(fileName).normalize();

        if (!filePath.startsWith(uploadDir)) {
            throw new NoSuchFileException("File not found: " + fileName);
        }

        Resource resource = new UrlResource(filePath.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            throw new NoSuchFileException("File not found: " + fileName);
        }

        return resource;
    }

    private @NonNull String getFileName(MultipartFile file) throws IOException {
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path uploadPath = uploadDir();
        Files.createDirectories(uploadPath);
        Files.copy(file.getInputStream(), uploadPath.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        return fileName;
    }

    private Path uploadDir() {
        return Paths.get(storageProperties.getLocal().getUploadDir()).toAbsolutePath().normalize();
    }

    private static String fileNameFromUrl(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }
}