package br.com.clicklance.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "clicklance.storage", name = "mode", havingValue = "local", matchIfMissing = true)
    LocalVideoStorage localVideoStorage(StorageProperties properties, Clock clock) {
        return new LocalVideoStorage(properties, clock);
    }

    @Configuration
    @ConditionalOnProperty(prefix = "clicklance.storage", name = "mode", havingValue = "s3")
    static class S3StorageConfiguration {
        @Bean(destroyMethod = "close")
        S3Client s3Client(StorageProperties properties) {
            properties.validate();
            var builder = S3Client.builder().region(Region.of(properties.getRegion()))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(properties.isPathStyleAccess()).build());
            if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
                builder.endpointOverride(URI.create(properties.getEndpoint()));
            }
            return builder.build();
        }

        @Bean(destroyMethod = "close")
        S3Presigner s3Presigner(StorageProperties properties) {
            var builder = S3Presigner.builder().region(Region.of(properties.getRegion()))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(properties.isPathStyleAccess()).build());
            if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
                builder.endpointOverride(URI.create(properties.getEndpoint()));
            }
            return builder.build();
        }

        @Bean
        VideoStorage s3VideoStorage(StorageProperties properties, S3Client client, S3Presigner presigner, Clock clock) {
            return new S3VideoStorage(properties, client, presigner, clock);
        }
    }
}
