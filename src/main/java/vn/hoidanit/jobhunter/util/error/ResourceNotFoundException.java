package vn.hoidanit.jobhunter.util.error;

// A requested record does not exist: mapped to HTTP 404.
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
