package br.com.clicklance.config;

import br.com.clicklance.service.PurgeStorageService;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods=false)
public class PurgeStorageConfiguration {
    @Bean(destroyMethod="close")
    @ConditionalOnMissingBean(PurgeStorageService.class)
    PurgeStorageService purgeStorageService(@Value("${clicklance.purge.enabled:false}") boolean enabled,
            @Value("${clicklance.purge.profile:}") String profile,
            @Value("${clicklance.storage.region:us-east-1}") String region,
            @Value("${clicklance.purge.endpoint:}") String endpoint) {
        if(!enabled) return new PurgeStorageService(null);
        if(profile.isBlank()) throw new IllegalStateException("Configure perfil AWS dedicado para purga.");
        var builder=S3Client.builder().region(Region.of(region))
                .credentialsProvider(ProfileCredentialsProvider.create(profile))
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(3)).socketTimeout(Duration.ofSeconds(5)))
                .overrideConfiguration(c->c.apiCallTimeout(Duration.ofSeconds(10)).apiCallAttemptTimeout(Duration.ofSeconds(7)).retryPolicy(r->r.numRetries(0)));
        if(!endpoint.isBlank()) {
            URI uri=URI.create(endpoint);
            if(!SetOfSchemes.contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null) throw new IllegalStateException("Endpoint de purga invalido.");
            builder.endpointOverride(uri).forcePathStyle(true);
        }
        return new PurgeStorageService(builder.build());
    }
    private static final java.util.Set<String> SetOfSchemes=java.util.Set.of("http","https");
}
