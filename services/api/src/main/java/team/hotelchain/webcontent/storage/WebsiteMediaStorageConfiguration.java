package team.hotelchain.webcontent.storage;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebsiteMediaStorageProperties.class)
public class WebsiteMediaStorageConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnExpression("'${website.media.storage.mode:local}' != 'local'")
    S3Client websiteMediaS3Client(WebsiteMediaStorageProperties properties) {
        WebsiteMediaStorageProperties.S3 settings = properties.getS3();
        requireText(settings.getRegion(), "region");
        requireText(settings.getBucket(), "bucket");

        DefaultCredentialsProvider credentials = DefaultCredentialsProvider.create();
        try {
            credentials.resolveCredentials();
        } catch (RuntimeException cause) {
            credentials.close();
            throw invalidConfiguration("AWS 자격 증명을 확인할 수 없습니다.", cause);
        }

        try {
            S3ClientBuilder builder = S3Client.builder()
                    .httpClientBuilder(UrlConnectionHttpClient.builder())
                    .credentialsProvider(credentials)
                    .region(Region.of(settings.getRegion().trim()))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(settings.isPathStyle())
                            .build());
            if (settings.getEndpoint() != null && !settings.getEndpoint().isBlank()) {
                builder.endpointOverride(URI.create(settings.getEndpoint().trim()));
            }
            return builder.build();
        } catch (RuntimeException cause) {
            credentials.close();
            throw invalidConfiguration("S3 클라이언트를 만들 수 없습니다.", cause);
        }
    }

    @Bean
    @ConditionalOnExpression("'${website.media.storage.mode:local}' != 'local'")
    S3WebsiteMediaObjectStore s3WebsiteMediaObjectStore(
            S3Client websiteMediaS3Client,
            WebsiteMediaStorageProperties properties) {
        return new S3WebsiteMediaObjectStore(websiteMediaS3Client, properties.getS3().getBucket());
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalidConfiguration("필수 항목이 비어 있습니다: " + field, null);
        }
    }

    private IllegalStateException invalidConfiguration(String detail, Throwable cause) {
        String message = "미디어 S3 저장소 설정이 올바르지 않습니다. " + detail;
        return cause == null ? new IllegalStateException(message) : new IllegalStateException(message, cause);
    }
}
