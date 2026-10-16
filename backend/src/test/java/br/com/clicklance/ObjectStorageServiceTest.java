package br.com.clicklance;

import br.com.clicklance.exception.ApiException;
import br.com.clicklance.model.ReplayInput;
import br.com.clicklance.security.DevicePrincipal;
import br.com.clicklance.service.ObjectStorageService;
import java.net.SocketTimeoutException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ObjectStorageServiceTest {
    @Test void timeoutRemainsRetryableAndUnauthorizedReferenceNeverCallsSdk() {
        var s3=mock(S3Client.class); var storage=new ObjectStorageService(s3);
        var principal=new DevicePrincipal("device","court","company","private-bucket");
        var input=new ReplayInput("a".repeat(32),"device","court",Instant.now(),"private-bucket","replays/device/"+"a".repeat(32)+".mp4","b".repeat(64));
        when(s3.headObject(any(HeadObjectRequest.class))).thenThrow(SdkClientException.builder().message("synthetic").cause(new SocketTimeoutException()).build());
        assertThatThrownBy(()->storage.verify(principal,input)).isInstanceOfSatisfying(ApiException.class,e -> {
            assertThat(e.status()).isEqualTo(503); assertThat(e.code()).isEqualTo("OBJECT_NOT_READY");
        });
        reset(s3);
        var foreign=new ReplayInput(input.eventId(),"other",input.courtId(),input.eventAt(),input.bucket(),input.key(),input.sha256());
        assertThatThrownBy(()->storage.verify(principal,foreign)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(403));
        verifyNoInteractions(s3);
    }
}
