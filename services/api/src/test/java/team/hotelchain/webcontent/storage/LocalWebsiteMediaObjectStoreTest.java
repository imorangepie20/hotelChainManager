package team.hotelchain.webcontent.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LocalWebsiteMediaObjectStoreTest {
    private static final UUID TRANSACTION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    @TempDir
    Path root;

    @Test
    void writesReadsListsAndDeletesOnlyRelativeKeys() {
        var store = new LocalWebsiteMediaObjectStore(root);
        byte[] bytes = "image".getBytes(StandardCharsets.UTF_8);
        store.put("asset.png", bytes, "image/png", WebsiteMediaDigest.sha256(bytes));

        assertThat(store.get("asset.png")).containsExactly(bytes);
        assertThat(store.head("asset.png")).get().satisfies(metadata -> {
            assertThat(metadata.key()).isEqualTo("asset.png");
            assertThat(metadata.byteSize()).isEqualTo(bytes.length);
            assertThat(metadata.sha256()).isEqualTo(WebsiteMediaDigest.sha256(bytes));
        });
        assertThat(store.list())
                .extracting(WebsiteMediaObjectMetadata::key)
                .containsExactly("asset.png");

        store.deleteIfExists("asset.png");

        assertThat(store.head("asset.png")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "../secret", "/absolute", "C:/absolute", ".trash/hidden"})
    void rejectsUnsafePublicKeys(String key) {
        assertThatThrownBy(() -> WebsiteMediaStorageKey.publicKey(key))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void quarantinesRestoresAndPurgesWithoutChangingThePublicKey() {
        var store = new LocalWebsiteMediaObjectStore(root);
        byte[] bytes = "variant".getBytes(StandardCharsets.UTF_8);
        store.put("variants/asset.webp", bytes, "image/webp", WebsiteMediaDigest.sha256(bytes));

        var quarantined = store.quarantine("variants/asset.webp", TRANSACTION_ID);

        assertThat(store.head("variants/asset.webp")).isEmpty();
        store.restore(quarantined);
        assertThat(store.get("variants/asset.webp")).containsExactly(bytes);

        var second = store.quarantine("variants/asset.webp", TRANSACTION_ID);
        store.purge(second);

        assertThat(store.head("variants/asset.webp")).isEmpty();
    }
}
