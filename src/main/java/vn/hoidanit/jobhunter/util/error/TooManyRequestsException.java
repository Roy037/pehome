package vn.hoidanit.jobhunter.util.error;

// The caller is retrying too fast (for example repeated failed log-ins): mapped to HTTP 429.
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
