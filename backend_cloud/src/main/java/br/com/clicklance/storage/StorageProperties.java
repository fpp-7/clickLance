package br.com.clicklance.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "clicklance.storage")
public class StorageProperties {
    private String mode = "local";
    private String bucket;
    private String region = "us-east-1";
    private String endpoint;
    private boolean pathStyleAccess;
    private Path localDirectory = Path.of("data", "videos");
    private String signingSecret = "";
    private long maxBytes = 250_000_000L;
    private long playbackTtlSeconds = 300;

    public void validate() {
        if (!"local".equals(mode) && !"s3".equals(mode)) {
            throw new IllegalArgumentException("clicklance.storage.mode deve ser local ou s3");
        }
        if (maxBytes <= 0 || playbackTtlSeconds < 2 || playbackTtlSeconds > 3600) {
            throw new IllegalArgumentException("Storage exige max-bytes positivo e TTL entre 2 e 3600 segundos");
        }
        if ("s3".equals(mode) && (bucket == null || bucket.isBlank() || region == null || region.isBlank())) {
            throw new IllegalArgumentException("Bucket e região são obrigatórios no modo s3");
        }
        if ("local".equals(mode) && (signingSecret == null || signingSecret.length() < 32)) {
            throw new IllegalArgumentException("Storage local exige signing-secret com pelo menos 32 caracteres");
        }
    }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public boolean isPathStyleAccess() { return pathStyleAccess; }
    public void setPathStyleAccess(boolean value) { pathStyleAccess = value; }
    public Path getLocalDirectory() { return localDirectory; }
    public void setLocalDirectory(Path localDirectory) { this.localDirectory = localDirectory; }
    public String getSigningSecret() { return signingSecret; }
    public void setSigningSecret(String signingSecret) { this.signingSecret = signingSecret; }
    public long getMaxBytes() { return maxBytes; }
    public void setMaxBytes(long maxBytes) { this.maxBytes = maxBytes; }
    public long getPlaybackTtlSeconds() { return playbackTtlSeconds; }
    public void setPlaybackTtlSeconds(long value) { playbackTtlSeconds = value; }
}
