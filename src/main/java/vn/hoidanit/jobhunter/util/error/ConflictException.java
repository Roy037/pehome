package vn.hoidanit.jobhunter.util.error;

// The request clashes with existing data (duplicate, or still referenced): mapped to HTTP 409.
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
