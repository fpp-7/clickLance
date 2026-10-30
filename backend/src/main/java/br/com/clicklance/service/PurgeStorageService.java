package br.com.clicklance.service;

import br.com.clicklance.exception.ApiException;
import br.com.clicklance.model.PurgeJob;
import br.com.clicklance.model.ReconciliationOrigin;
import java.util.*;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/** Cliente dedicado, opt-in; nunca reutiliza credenciais de ingestao. */
public class PurgeStorageService implements AutoCloseable {
    private final S3Client s3;
    public PurgeStorageService(S3Client s3) { this.s3=s3; }
    public void requireEnabled() { if(s3==null) throw new ApiException(503,"PURGE_DISABLED"); }
    public void deleteAndConfirm(PurgeJob job) {
        requireEnabled();
        if(!job.key().equals("replays/"+job.deviceId()+"/"+job.eventId()+".mp4") || job.version()!=null) throw new ApiException(503,"STORAGE_POLICY_MISMATCH");
        try {
            unversioned(job.bucket());
            if(exists(job.bucket(),job.key())) {
                var head=s3.headObject(HeadObjectRequest.builder().bucket(job.bucket()).key(job.key()).build());
                if(head.versionId()!=null && !head.versionId().equals("null") || !Objects.equals(job.etag(),head.eTag())) throw new ApiException(503,"STORAGE_POLICY_MISMATCH");
                s3.deleteObject(DeleteObjectRequest.builder().bucket(job.bucket()).key(job.key()).ifMatch(job.etag()).build());
            }
            unversioned(job.bucket());
            if(exists(job.bucket(),job.key())) throw new ApiException(503,"DELETE_NOT_CONFIRMED");
        } catch(SdkException failure) { throw new ApiException(503,"STORAGE_UNAVAILABLE"); }
    }
    private void unversioned(String bucket) {
        if(s3.getBucketVersioning(GetBucketVersioningRequest.builder().bucket(bucket).build()).statusAsString()!=null) throw new ApiException(503,"STORAGE_POLICY_MISMATCH");
    }
    public boolean exists(String bucket,String key) {
        requireEnabled();
        try { s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()); return true; }
        catch(S3Exception failure) {
            if(failure.statusCode()==404) return false;
            throw new ApiException(503,"STORAGE_UNAVAILABLE");
        } catch(SdkException failure) { throw new ApiException(503,"STORAGE_UNAVAILABLE"); }
    }
    public Set<String> keys(ReconciliationOrigin origin) {
        requireEnabled();
        String prefix="replays/"+origin.deviceId()+"/";
        try {
            var result=new LinkedHashSet<String>();
            for(var page:s3.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(origin.bucket()).prefix(prefix).build())) {
                for(var object:page.contents()) {
                    if(object.key()==null || !object.key().startsWith(prefix) || object.key().length()>1024) throw new ApiException(503,"STORAGE_POLICY_MISMATCH");
                    result.add(object.key());
                    if(result.size()>100000) throw new ApiException(503,"RECONCILIATION_LIMIT");
                }
            }
            return result;
        } catch(SdkException failure) { throw new ApiException(503,"STORAGE_UNAVAILABLE"); }
    }
    @Override public void close() { if(s3!=null) s3.close(); }
}
