package br.com.clicklance.service;

import br.com.clicklance.exception.ApiException;
import br.com.clicklance.model.MediaAccess;
import br.com.clicklance.model.PlayerReplay;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
public class MediaService {
    private final S3Presigner presigner;
    public MediaService(S3Presigner presigner) { this.presigner=presigner; }
    public MediaAccess issue(PlayerReplay replay,boolean download,Instant now) {
        long seconds=replay.saved()?300:Math.min(300,Duration.between(now,replay.expiresAt()).getSeconds());
        if (seconds<1) throw new ApiException(404,"RESOURCE_NOT_FOUND");
        Instant ceiling=replay.saved()?now.plusSeconds(300):
                (replay.expiresAt().isBefore(now.plusSeconds(300))?replay.expiresAt():now.plusSeconds(300));
        var get=GetObjectRequest.builder().bucket(replay.bucket()).key(replay.objectKey())
                .responseContentType("video/mp4")
                .responseContentDisposition((download?"attachment":"inline")+"; filename=\""+replay.eventId()+".mp4\"");
        if (replay.objectVersion()!=null) get.versionId(replay.objectVersion());
        try {
            // Credential resolution/signing can take time. Verify the actual SDK expiration,
            // rather than assuming the signature starts at the authorization clock instant.
            for(int attempt=0;attempt<3 && seconds>0;attempt++) {
                var signed=presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofSeconds(seconds)).getObjectRequest(get.build()).build());
                Instant actual=signed.expiration();
                if (!actual.isAfter(now)) throw new ApiException(404,"RESOURCE_NOT_FOUND");
                if (!actual.isAfter(ceiling)) return new MediaAccess(signed.url().toExternalForm(),actual);
                long excess=Duration.between(ceiling,actual).toMillis();
                seconds-=Math.max(1,(excess+999)/1000);
            }
            throw new ApiException(404,"RESOURCE_NOT_FOUND");
        } catch (SdkException ignored) { throw new ApiException(503,"DEPENDENCY_UNAVAILABLE"); }
    }
}
