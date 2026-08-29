package org.margin.server.integrationtest;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.margin.server.storage.StorageProperties;
import org.margin.server.storage.services.S3StorageService;
import org.margin.server.storage.services.StorageService;
import org.margin.server.storage.services.StorageUrls;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.MinIOContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class S3StorageIntegrationTest {
    private static final MinIOContainer minio = new MinIOContainer("minio/minio:latest");
    private static StorageService storage;

    @BeforeAll
    static void setUp() {
        minio.start();

        StorageProperties props = new StorageProperties();
        props.setType("s3");
        StorageProperties.S3Properties s3 = props.getS3();
        s3.setEndpoint(minio.getS3URL());
        s3.setRegion("eu-central");
        s3.setBucketName("margin");
        s3.setAccessKey(minio.getUserName());
        s3.setSecretKey(minio.getPassword());
        s3.setBaseUrl("https://margin.chat/api/files");
        s3.setCdnBaseUrl("https://files.margin.chat");

        createBucket(s3);

        storage = new S3StorageService(props);
        new StorageUrls(storage, props);
    }

    @AfterAll
    static void tearDown() {
        StorageProperties local = new StorageProperties();
        local.setType("local");
        new StorageUrls(storage, local);
        minio.stop();
    }

    private static void createBucket(StorageProperties.S3Properties s3) {
        try (S3Client client = S3Client.builder()
                .endpointOverride(URI.create(s3.getEndpoint()))
                .region(Region.of(s3.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey())))
                .forcePathStyle(true)
                .build()) {
            client.createBucket(CreateBucketRequest.builder().bucket(s3.getBucketName()).build());
        }
    }

    @Test
    void avatarUpload_isRewrittenToPublicCdnUrl() {
        MockMultipartFile file = new MockMultipartFile("file", "me.png", "image/png", "avatar-bytes".getBytes());

        String publicUrl = StorageUrls.publicUrl(storage.saveProfilePicture(file));

        assertThat(publicUrl).startsWith("https://files.margin.chat/user-profiles/");
        assertThat(publicUrl).endsWith("_me.png");
    }

    @Test
    void storedFileUpload_isPresignedAndDownloadable() throws Exception {
        byte[] content = "hello world".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", content);

        String signedUrl = StorageUrls.signedUrl(storage.saveStoredFile(file));

        assertThat(signedUrl).contains("X-Amz-Signature");
        assertThat(signedUrl).doesNotContain("/api/");

        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(signedUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(content);
    }

    @Test
    void storedFile_withForeignEndpointHostInUrl_stillPresignsAndDownloads() throws Exception {
        byte[] content = "cross-host bytes".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", content);

        // Upload puts the object at key stored/<uuid>_report.pdf and returns a baseUrl-style URL.
        String baseUrlForm = storage.saveStoredFile(file);
        String fileName = baseUrlForm.substring(baseUrlForm.lastIndexOf('/') + 1);

        // A storage_url persisted as a path-style URL whose host differs from the configured
        // endpoint — the prod misconfig that previously made presign throw and leak the raw URL.
        String foreignHostUrl = "https://hel1.your-objectstorage.com/margin/stored/" + fileName;

        String signedUrl = StorageUrls.signedUrl(foreignHostUrl);

        assertThat(signedUrl).contains("X-Amz-Signature");
        assertThat(signedUrl).doesNotContain("hel1.your-objectstorage.com");

        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(signedUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(content);
    }
}