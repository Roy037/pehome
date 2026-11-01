package vn.hoidanit.jobhunter.util.error;

// The payment gateway has no credentials yet: mapped to HTTP 503.
public class GatewayNotConfiguredException extends RuntimeException {
    public GatewayNotConfiguredException(String message) {
        super(message);
    }
}
