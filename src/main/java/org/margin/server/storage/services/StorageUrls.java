package org.margin.server.storage.services;

import org.margin.server.storage.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StorageUrls {
    private static final Logger log = LoggerFactory.getLogger(StorageUrls.class);
    private static final String[] PUBLIC_MARKERS = {"/user-profiles/", "/margin-icons/"};

    private static volatile StorageService storage;
    private static volatile String cdnBaseUrl;
    private static volatile boolean s3;

    public StorageUrls(StorageService storage, StorageProperties properties) {
        StorageUrls.storage = storage;
        StorageUrls.cdnBaseUrl = properties.getS3().getCdnBaseUrl();
        StorageUrls.s3 = "s3".equalsIgnoreCase(properties.getType());
    }

    public static String publicUrl(String storedUrl) {
        if (storedUrl == null || !s3 || cdnBaseUrl == null || cdnBaseUrl.isBlank()) {
            return storedUrl;
        }
        for (String marker : PUBLIC_MARKERS) {
            int idx = storedUrl.indexOf(marker);
            if (idx >= 0) {
                return cdnBaseUrl + storedUrl.substring(idx);
            }
        }
        return storedUrl;
    }

    public static String signedUrl(String storedUrl) {
        if (storedUrl == null || !s3 || storage == null) {
            return storedUrl;
        }
        try {
            return storage.presign(storedUrl).orElse(storedUrl);
        } catch (RuntimeException e) {
            log.warn("Failed to presign storage URL '{}'; serving raw URL. Check that HETZNER_S3_ENDPOINT matches the stored host. Cause: {}",
                    storedUrl, e.getMessage());
            return storedUrl;
        }
    }
}
