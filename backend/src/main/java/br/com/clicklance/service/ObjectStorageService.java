package br.com.clicklance.service;

import br.com.clicklance.exception.ApiException;
import br.com.clicklance.model.ReplayInput;
import br.com.clicklance.model.VerifiedObject;
import br.com.clicklance.security.DevicePrincipal;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

/** Adaptador S3 somente leitura. Nenhuma URL do request chega ao SDK. */
@Service
public class ObjectStorageService {
    private final S3Client s3;
    public ObjectStorageService(S3Client s3) { this.s3=s3; }
    public void authorize(DevicePrincipal principal, ReplayInput input) {
        if (!principal.deviceId().equals(input.deviceId()) || !principal.courtId().equals(input.courtId())
                || !principal.bucket().equals(input.bucket()) || !input.key().equals("replays/"+principal.deviceId()+"/"+input.eventId()+".mp4")) throw new ApiException(403,"FORBIDDEN");
    }
    public VerifiedObject verify(DevicePrincipal principal, ReplayInput input) {
        authorize(principal,input);
        try {
            var head=s3.headObject(HeadObjectRequest.builder().bucket(principal.bucket()).key(input.key()).build());
            if (head.contentLength()==null || head.contentLength()<=0) throw new ApiException(422,"VALIDATION_FAILED");
            if (!input.sha256().equals(head.metadata().get("sha256"))) throw new ApiException(409,"CONTENT_CONFLICT");
            String version=head.versionId();
            if (head.eTag()==null || head.eTag().isBlank()) throw new ApiException(503,"OBJECT_NOT_READY");
            var request=GetObjectRequest.builder().bucket(principal.bucket()).key(input.key());
            if (version!=null && !version.isBlank() && !version.equals("null")) request.versionId(version);
            else { version=null; request.ifMatch(head.eTag()); }
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            long count=0;
            try (var stream=s3.getObject(request.build())) {
                var response=stream.response();
                if (!head.eTag().equals(response.eTag()) || (version!=null && !version.equals(response.versionId()))) throw new ApiException(503,"OBJECT_NOT_READY");
                byte[] buffer=new byte[65536]; int read;
                while ((read=stream.read(buffer))!=-1) {
                    count+=read;
                    if (count>head.contentLength()) throw new ApiException(503,"OBJECT_NOT_READY");
                    digest.update(buffer,0,read);
                }
            }
            if (count!=head.contentLength()) throw new ApiException(503,"OBJECT_NOT_READY");
            if (!input.sha256().equals(HexFormat.of().formatHex(digest.digest()))) throw new ApiException(409,"CONTENT_CONFLICT");
            return new VerifiedObject(version,head.eTag(),count);
        } catch (SdkException | IOException ignored) { throw new ApiException(503,"OBJECT_NOT_READY"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 indisponivel"); }
    }
}
