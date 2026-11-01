package vn.hoidanit.jobhunter.oauth;

/** A sign-in that cannot go on. The code is what the log-in page shows (?oauth_error=code); the message is for the log. */
public class OAuthException extends RuntimeException {
    public static final String DENIED = "denied";
    public static final String DISABLED = "disabled";
    public static final String NO_EMAIL = "no_email";
    public static final String UNVERIFIED_EMAIL = "unverified_email";
    public static final String LOCKED = "locked";
    public static final String UNSUPPORTED_ACCOUNT = "unsupported_account";
    public static final String FAILED = "failed";

    private final String code;

    public OAuthException(String code, String message) {
        super(message);
        this.code = code;
    }

    public OAuthException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return this.code;
    }
}
