package org.margin.server.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {
    private String type = "local";
    private S3Properties s3 = new S3Properties();
    private LocalProperties local = new LocalProperties();

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public S3Properties getS3() { return s3; }
    public void setS3(S3Properties s3) { this.s3 = s3; }
    public LocalProperties getLocal() { return local; }
    public void setLocal(LocalProperties local) { this.local = local; }

    public static class S3Properties {
        private String endpoint;
        private String region;
        private String bucketName;
        private String accessKey;
        private String secretKey;

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getBucketName() { return bucketName; }
        public void setBucketName(String bucketName) { this.bucketName = bucketName; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    }

    public static class LocalProperties {
        private String uploadDir;
        private String baseUrl;

        public String getUploadDir() { return uploadDir; }
        public void setUploadDir(String uploadDir) { this.uploadDir = uploadDir; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }
}