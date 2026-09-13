package team.hotelchain.webcontent.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@EnabledIfSystemProperty(named = "website.media.s3.test", matches = "true")
class S3WebsiteMediaObjectStoreIntegrationTest {
    private S3Client client;
    private S3WebsiteMediaObjectStore store;
    private String bucket;

    @BeforeEach
    void setUp() {
        bucket = "hotel-media-" + UUID.randomUUID().toString();
        client = S3Client.builder()
                .endpointOverride(URI.create("http://127.0.0.1:59090"))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("s3mock-local", "s3mock-local")))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
        client.createBucket(request -> request.bucket(bucket));
        store = new S3WebsiteMediaObjectStore(client, bucket);
    }

    @AfterEach
    void tearDown() {
        if (client == null) return;
        client.listObjectsV2Paginator(request -> request.bucket(bucket)).contents()
                .forEach(object -> client.deleteObject(request -> request.bucket(bucket).key(object.key())));
        client.deleteBucket(request -> request.bucket(bucket));
        client.close();
    }

    @Test
    void writesReadsHeadsListsAndRejectsDifferentOverwrite() {
        byte[] bytes = "s3-image".getBytes(StandardCharsets.UTF_8);
        String digest = WebsiteMediaDigest.sha256(bytes);

        store.put("asset.png", bytes, "image/png", digest);
        store.put("asset.png", bytes, "image/png", digest);

        assertThat(store.get("asset.png")).containsExactly(bytes);
        assertThat(store.head("asset.png")).get().satisfies(metadata -> {
            assertThat(metadata.byteSize()).isEqualTo(bytes.length);
            assertThat(metadata.sha256()).isEqualTo(digest);
        });
        assertThat(store.list()).extracting(WebsiteMediaObjectMetadata::key).containsExactly("asset.png");
        assertThatThrownBy(() -> store.put(
                "asset.png", "different".getBytes(StandardCharsets.UTF_8), "image/png",
                WebsiteMediaDigest.sha256("different".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(WebsiteMediaStorageException.class);
    }

    @Test
    void quarantinesRestoresAndPurges() {
        byte[] bytes = "variant".getBytes(StandardCharsets.UTF_8);
        store.put("variant.webp", bytes, "image/webp", WebsiteMediaDigest.sha256(bytes));

        var quarantined = store.quarantine(
                "variant.webp", UUID.fromString("20000000-0000-0000-0000-000000000001"));
        assertThat(store.head("variant.webp")).isEmpty();

        store.restore(quarantined);
        assertThat(store.get("variant.webp")).containsExactly(bytes);

        var second = store.quarantine(
                "variant.webp", UUID.fromString("20000000-0000-0000-0000-000000000002"));
        store.purge(second);
        assertThat(store.head("variant.webp")).isEmpty();
    }

    @Test
    void paginatesMoreThanOneThousandObjects() {
        for (int index = 0; index < 1001; index++) {
            byte[] bytes = Integer.toString(index).getBytes(StandardCharsets.UTF_8);
            store.put("page/" + index + ".webp", bytes, "image/webp", WebsiteMediaDigest.sha256(bytes));
        }

        assertThat(store.list()).hasSize(1001);
    }
}
