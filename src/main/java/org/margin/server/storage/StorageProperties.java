package org.margin.server.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {
    private String type = "local";
    private S3Properties s3 = new S3Properties();
    private LocalProperties local = new LocalProperties();

    @Setter
    @Getter
    public static class S3Properties {
        private String endpoint;
        private String region;
        private String bucketName;
        private String accessKey;
        private String secretKey;
        private String baseUrl;
        private String cdnBaseUrl;
        private long presignTtlSeconds = 600;
    }

    @Setter
    @Getter
    public static class LocalProperties {
        private String uploadDir;
        private String baseUrl;

    }
}