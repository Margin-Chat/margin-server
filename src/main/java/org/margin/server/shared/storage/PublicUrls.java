package org.margin.server.shared.storage;

public final class PublicUrls {

    private static final String[] PUBLIC_MARKERS = {"/user-profiles/", "/margin-icons/"};

    private static volatile String cdnBaseUrl;
    private static volatile boolean s3;

    private PublicUrls() {
    }

    public static void configure(String cdnBaseUrl, boolean s3) {
        PublicUrls.cdnBaseUrl = cdnBaseUrl;
        PublicUrls.s3 = s3;
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
}
