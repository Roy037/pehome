package vn.hoidanit.jobhunter.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import vn.hoidanit.jobhunter.util.Hmac;

/**
 * The {@code state} parameter of the OAuth round trip: {@code nonce.expiry.next.signature}. The nonce also travels in a
 * short-lived cookie, so a callback only counts when it comes back to the browser that started it (login CSRF), and the
 * signature stops anyone from changing {@code next} (where the user lands) or the expiry.
 */
@Component
public class OAuthState {

    static final Duration TTL = Duration.ofMinutes(10);
    private static final Pattern SAFE_PATH = Pattern.compile("^/[A-Za-z0-9/_\\-.,:;=&?%#+~]{0,198}$");
    private static final String DOMAIN = "itjobs-oauth-state:";

    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public OAuthState(@Value("${hoidanit.jwt.base64-secret}") String secret) {
        this.key = Hmac.key(secret);
    }

    public String newNonce() {
        byte[] bytes = new byte[24];
        this.random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Only a path on this site (the app route to return to) is accepted; anything else becomes the home page. */
    public static String safeNext(String next) {
        return next != null && !next.startsWith("//") && SAFE_PATH.matcher(next).matches() ? next : "/";
    }

    public String create(String nonce, String next) {
        return create(nonce, next, Instant.now().plus(TTL));
    }

    String create(String nonce, String next, Instant expiry) {
        String encodedNext = Base64.getUrlEncoder().withoutPadding().encodeToString(safeNext(next).getBytes(StandardCharsets.UTF_8));
        String payload = nonce + "." + expiry.getEpochSecond() + "." + encodedNext;
        return payload + "." + Hmac.sign(this.key, DOMAIN + payload);
    }

    /** @return where the user should land, when the state is ours, unexpired and matches the nonce cookie */
    public Optional<String> verify(String state, String cookieNonce) {
        if (state == null || cookieNonce == null || cookieNonce.isBlank()) {
            return Optional.empty();
        }
        String[] parts = state.split("\\.", -1);
        if (parts.length != 4) {
            return Optional.empty();
        }
        String payload = parts[0] + "." + parts[1] + "." + parts[2];
        byte[] given = parts[3].getBytes(StandardCharsets.UTF_8);
        byte[] expected = Hmac.sign(this.key, DOMAIN + payload).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(given, expected)
                || !MessageDigest.isEqual(parts[0].getBytes(StandardCharsets.UTF_8), cookieNonce.getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        try {
            if (Instant.ofEpochSecond(Long.parseLong(parts[1])).isBefore(Instant.now())) {
                return Optional.empty();
            }
            return Optional.of(safeNext(new String(Base64.getUrlDecoder().decode(parts[2]), StandardCharsets.UTF_8)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
