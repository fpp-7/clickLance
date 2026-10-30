package br.com.clicklance.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods=false)
public class MediaConfiguration {
    @Bean(destroyMethod="close")
    S3Presigner mediaPresigner(@Value("${clicklance.storage.region:us-east-1}") String region,
                              @Value("${clicklance.storage.endpoint:}") String endpoint) {
        var builder=S3Presigner.builder().region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.builder().build());
        if (!endpoint.isBlank()) {
            var uri=URI.create(endpoint);
            if (!java.util.Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null)
                throw new IllegalStateException("STORAGE_ENDPOINT invalido");
            builder.endpointOverride(uri).serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }
}
