package org.margin.server.storage.services;

import org.margin.server.storage.StorageProperties;
import org.margin.server.storage.exceptions.S3DeleteException;
import org.margin.server.storage.exceptions.S3RetrievalException;
import org.margin.server.storage.exceptions.S3UploadException;
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
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {
    private static final Map<String, String> PATH_TO_PREFIX = Map.of(
            "user-profiles", "users",
            "margin-icons", "margins",
            "conversation-images", "conversations",
            "stored-files", "stored"
    );

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties.S3Properties s3Props;
    private final String legacyKeyPrefix;

    public S3StorageService(StorageProperties storageProperties) {
        this.s3Props = storageProperties.getS3();

        AwsBasicCredentials credentials = AwsBasicCredentials.create(s3Props.getAccessKey(), s3Props.getSecretKey());
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);
        URI endpoint = URI.create(s3Props.getEndpoint());
        Region region = Region.of(s3Props.getRegion());

        this.s3Client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentialsProvider)
                .forcePathStyle(true)
                .build();

        this.s3Presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();

        this.legacyKeyPrefix = s3Props.getEndpoint() + "/" + s3Props.getBucketName() + "/";
    }

    @Override
    public String saveProfilePicture(MultipartFile file) {
        return upload(file, "user-profiles", "profile picture");
    }

    @Override
    public String saveMarginIcon(MultipartFile file) {
        return upload(file, "margin-icons", "margin icon");
    }

    @Override
    public String saveStoredFile(MultipartFile file) {
        return upload(file, "stored-files", "stored file");
    }

    private String upload(MultipartFile file, String urlPath, String label) {
        try {
            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            String key = PATH_TO_PREFIX.get(urlPath) + "/" + fileName;

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3Props.getBucketName())
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            return s3Props.getBaseUrl() + "/" + urlPath + "/" + fileName;
        } catch (Exception e) {
            throw new S3UploadException(label, e);
        }
    }

    @Override
    public void deleteProfilePicture(String url) {
        delete(url);
    }

    @Override
    public void delete(String url) {
        try {
            DeleteObjectRequest req = DeleteObjectRequest.builder()
                    .bucket(s3Props.getBucketName())
                    .key(toKey(url))
                    .build();
            s3Client.deleteObject(req);
        } catch (Exception e) {
            throw new S3DeleteException(e);
        }
    }

    @Override
    public Resource getFile(String url) {
        try {
            GetObjectRequest req = GetObjectRequest.builder()
                    .bucket(s3Props.getBucketName())
                    .key(toKey(url))
                    .build();
            return new InputStreamResource(s3Client.getObject(req));
        } catch (Exception e) {
            throw new S3RetrievalException(e);
        }
    }

    @Override
    public Optional<String> presign(String url, String downloadFilename) {
        GetObjectRequest.Builder getBuilder = GetObjectRequest.builder()
                .bucket(s3Props.getBucketName())
                .key(toKey(url));

        if (downloadFilename != null) {
            String safe = downloadFilename.replace("\"", "");
            getBuilder.responseContentDisposition("attachment; filename=\"" + safe + "\"");
        }

        GetObjectPresignRequest req = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(s3Props.getPresignTtlSeconds()))
                .getObjectRequest(getBuilder.build())
                .build();

        return Optional.of(s3Presigner.presignGetObject(req).url().toString());
    }

    private String toKey(String url) {
        if (url.startsWith(legacyKeyPrefix)) {
            return url.substring(legacyKeyPrefix.length());
        }
        for (Map.Entry<String, String> entry : PATH_TO_PREFIX.entrySet()) {
            String marker = "/" + entry.getKey() + "/";
            int idx = url.indexOf(marker);
            if (idx >= 0) {
                return entry.getValue() + "/" + url.substring(idx + marker.length());
            }
        }
        String bucketMarker = "/" + s3Props.getBucketName() + "/";
        int bucketIdx = url.indexOf(bucketMarker);
        if (bucketIdx >= 0) {
            return url.substring(bucketIdx + bucketMarker.length());
        }
        throw new IllegalArgumentException("Unrecognized storage URL: " + url);
    }
}