package org.margin.server.unittest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.margin.server.storage.StorageProperties;
import org.margin.server.storage.dtos.StoredFileDTO;
import org.margin.server.storage.models.StoredFile;
import org.margin.server.storage.services.StorageService;
import org.margin.server.storage.services.StorageUrls;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorageUrlsTest {

    private static final String CDN = "https://files.margin.chat";

    private StorageProperties s3Props() {
        StorageProperties p = new StorageProperties();
        p.setType("s3");
        p.getS3().setCdnBaseUrl(CDN);
        return p;
    }

    private StorageProperties localProps() {
        StorageProperties p = new StorageProperties();
        p.setType("local");
        return p;
    }

    private StorageService initS3() {
        StorageService storage = mock(StorageService.class);
        new StorageUrls(storage, s3Props());
        return storage;
    }

    /** Reset to safe pass-through so s3 mode never leaks into other test classes in the same JVM. */
    @AfterEach
    void resetToLocal() {
        new StorageUrls(mock(StorageService.class), localProps());
    }

    @Test
    void publicUrl_rewritesAvatarAndIconToCdnHost() {
        initS3();

        assertThat(StorageUrls.publicUrl("https://margin.chat/api/files/user-profiles/abc.png"))
                .isEqualTo("https://files.margin.chat/user-profiles/abc.png");
        assertThat(StorageUrls.publicUrl("/api/files/margin-icons/icon.png"))
                .isEqualTo("https://files.margin.chat/margin-icons/icon.png");
    }

    @Test
    void publicUrl_leavesPrivatePathsAndNullUnchanged() {
        initS3();

        assertThat(StorageUrls.publicUrl("/api/files/conversation-images/x.png"))
                .isEqualTo("/api/files/conversation-images/x.png");
        assertThat(StorageUrls.publicUrl(null)).isNull();
    }

    @Test
    void signedUrl_presignsPrivateMedia() {
        StorageService storage = initS3();
        when(storage.presign("/api/files/conversation-images/x.png"))
                .thenReturn(Optional.of("https://hel1.your-objectstorage.com/margin/conversations/x.png?sig=abc"));

        assertThat(StorageUrls.signedUrl("/api/files/conversation-images/x.png"))
                .isEqualTo("https://hel1.your-objectstorage.com/margin/conversations/x.png?sig=abc");
    }

    @Test
    void signedUrl_fallsBackToStoredUrlWhenPresignEmpty() {
        StorageService storage = initS3();
        when(storage.presign("/api/files/stored-files/x.bin")).thenReturn(Optional.empty());

        assertThat(StorageUrls.signedUrl("/api/files/stored-files/x.bin"))
                .isEqualTo("/api/files/stored-files/x.bin");
    }

    @Test
    void localMode_passesEverythingThrough() {
        new StorageUrls(mock(StorageService.class), localProps());

        assertThat(StorageUrls.publicUrl("/api/files/user-profiles/a.png"))
                .isEqualTo("/api/files/user-profiles/a.png");
        assertThat(StorageUrls.signedUrl("/api/files/conversation-images/a.png"))
                .isEqualTo("/api/files/conversation-images/a.png");
    }

    @Test
    void publicUrl_passesThroughWhenCdnBaseUrlBlank() {
        // s3 mode but no CDN configured (local dev) → keep the authenticated /api/files URL
        StorageProperties props = new StorageProperties();
        props.setType("s3");
        props.getS3().setCdnBaseUrl("");
        new StorageUrls(mock(StorageService.class), props);

        assertThat(StorageUrls.publicUrl("http://localhost:8080/api/files/user-profiles/a.png"))
                .isEqualTo("http://localhost:8080/api/files/user-profiles/a.png");
    }

    @Test
    void userDto_resolvesAvatarThroughPublicCdn() {
        initS3();
        User user = mock(User.class);
        when(user.getProfilePictureUrl()).thenReturn("/api/files/user-profiles/a.png");

        UserDTO dto = new UserDTO(user, true);

        assertThat(dto.profilePictureUrl()).isEqualTo("https://files.margin.chat/user-profiles/a.png");
    }

    @Test
    void storedFileDto_presignsPrivateUrl() {
        StorageService storage = initS3();
        when(storage.presign("/api/files/conversation-images/a.png"))
                .thenReturn(Optional.of("https://hel1.your-objectstorage.com/signed"));
        StoredFile f = mock(StoredFile.class);
        when(f.getStorageUrl()).thenReturn("/api/files/conversation-images/a.png");
        when(f.getUploadedBy()).thenReturn(mock(User.class));

        StoredFileDTO dto = StoredFileDTO.from(f, true);

        assertThat(dto.url()).isEqualTo("https://hel1.your-objectstorage.com/signed");
    }
}
