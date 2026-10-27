package br.com.clicklance.storage;

public class StorageException extends RuntimeException {
    public enum Reason { INVALID_KEY, NOT_FOUND, INVALID_VIDEO, EXPIRED, UNAVAILABLE, UNSAFE_CONFIGURATION }
    private final Reason reason;

    public StorageException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public StorageException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
