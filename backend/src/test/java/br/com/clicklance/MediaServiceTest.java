package br.com.clicklance;

import br.com.clicklance.exception.ApiException;
import br.com.clicklance.model.PlayerReplay;
import br.com.clicklance.service.MediaService;
import java.net.URI;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaServiceTest {
    static final Instant NOW=Instant.parse("2026-10-30T12:00:00Z");
    PlayerReplay replay() {
        return new PlayerReplay("a".repeat(32),"company","court",NOW.minusSeconds(1),NOW.plusSeconds(10),
                "private-bucket","safe-key","version1",true,false,false,false,false);
    }
    PresignedGetObjectRequest signed(Instant expiration) throws Exception {
        var request=mock(PresignedGetObjectRequest.class);
        when(request.expiration()).thenReturn(expiration);
        when(request.url()).thenReturn(URI.create("https://private-bucket.invalid/safe-key?signature=fixture").toURL());
        return request;
    }
    @Test void slowSigningRetriesWithShorterLifetimeAndReportsActualSignatureExpiration() throws Exception {
        var presigner=mock(S3Presigner.class);
        var delayed=signed(NOW.plusSeconds(13));
        var bounded=signed(NOW.plusSeconds(10));
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(delayed,bounded);
        var access=new MediaService(presigner).issue(replay(),false,NOW);
        assertThat(access.expira_em()).isEqualTo(NOW.plusSeconds(10));
        var captor=ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner,times(2)).presignGetObject(captor.capture());
        assertThat(captor.getAllValues()).extracting(r->r.signatureDuration().toSeconds()).containsExactly(10L,7L);
        assertThat(captor.getValue().getObjectRequest().versionId()).isEqualTo("version1");
    }
    @Test void signingThatCannotFitBeforeExpirationNeverReturnsUrl() throws Exception {
        var presigner=mock(S3Presigner.class);
        var tooLate=signed(NOW.plusSeconds(25));
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(tooLate);
        assertThatThrownBy(()->new MediaService(presigner).issue(replay(),true,NOW))
                .isInstanceOfSatisfying(ApiException.class,error->assertThat(error.status()).isEqualTo(404));
        verify(presigner).presignGetObject(any(GetObjectPresignRequest.class));
    }
}
