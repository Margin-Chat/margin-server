package org.margin.server.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final StorageProperties storageProperties;

    public S3StorageService(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;

        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                storageProperties.getS3().getAccessKey(),
                storageProperties.getS3().getSecretKey()
        );

        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(storageProperties.getS3().getEndpoint()))
                .region(Region.of(storageProperties.getS3().getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .forcePathStyle(true)
                .build();
    }

    @Override
    public String saveProfilePicture(MultipartFile file) {
        try {
            String fileName = "users/" + UUID.randomUUID() + "_" + file.getOriginalFilename();

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(storageProperties.getS3().getBucketName())
                    .key(fileName)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            return String.format("%s/%s/%s",
                    storageProperties.getS3().getEndpoint(),
                    storageProperties.getS3().getBucketName(),
                    fileName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload profile picture to S3", e);
        }
    }

    @Override
    public String saveMarginIcon(MultipartFile file) {
        try {
            String fileName = "margins/" + UUID.randomUUID() + "_" + file.getOriginalFilename();

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(storageProperties.getS3().getBucketName())
                    .key(fileName)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            return String.format("%s/%s/%s",
                    storageProperties.getS3().getEndpoint(),
                    storageProperties.getS3().getBucketName(),
                    fileName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload margin icon to S3", e);
        }
    }

    @Override
    public String saveConversationImage(MultipartFile file) {
        try {
            String fileName = "conversations/" + UUID.randomUUID() + "_" + file.getOriginalFilename();

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(storageProperties.getS3().getBucketName())
                    .key(fileName)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            return String.format("%s/%s/%s",
                    storageProperties.getS3().getEndpoint(),
                    storageProperties.getS3().getBucketName(),
                    fileName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload conversation image to S3", e);
        }
    }

    @Override
    public void deleteProfilePicture(String url) {
        try {
            String key = extractKeyFromUrl(url);
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(storageProperties.getS3().getBucketName())
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file from S3", e);
        }
    }

    @Override
    public Resource getFile(String url) {
        try {
            String key = extractKeyFromUrl(url);

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(storageProperties.getS3().getBucketName())
                    .key(key)
                    .build();

            var s3Object = s3Client.getObject(getObjectRequest);
            return new InputStreamResource(s3Object);
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve profile picture from S3", e);
        }
    }

    private String extractKeyFromUrl(String url) {
        String bucketUrl = String.format("%s/%s/",
                storageProperties.getS3().getEndpoint(),
                storageProperties.getS3().getBucketName());
        return url.replace(bucketUrl, "");
    }
}