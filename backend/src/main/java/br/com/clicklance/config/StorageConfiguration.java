package br.com.clicklance.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods=false)
public class StorageConfiguration {
    @Bean(destroyMethod="close")
    S3Client s3Client(@Value("${clicklance.storage.region:us-east-1}") String region,
                      @Value("${clicklance.storage.endpoint:}") String endpoint) {
        var builder=S3Client.builder().region(Region.of(region)).credentialsProvider(DefaultCredentialsProvider.builder().build())
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(15)))
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(30)).apiCallAttemptTimeout(Duration.ofSeconds(20)));
        if (!endpoint.isBlank()) {
            URI uri=URI.create(endpoint);
            if (!java.util.Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null) throw new IllegalStateException("STORAGE_ENDPOINT invalido");
            builder.endpointOverride(uri).forcePathStyle(true);
        }
        return builder.build();
    }
}
