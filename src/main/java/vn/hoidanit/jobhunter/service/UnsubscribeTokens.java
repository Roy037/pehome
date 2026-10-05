package vn.hoidanit.jobhunter.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.OptionalLong;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import vn.hoidanit.jobhunter.util.Hmac;

/**
 * Signed, stateless token for the "unsubscribe" link in the weekly digest: {@code <subscriberId>.<HMAC-SHA256>}.
 * It never expires (an old e-mail must still work) and proves nothing but "this link was issued by us for that
 * subscriber", which is all one-click unsubscribe needs. It is a raw HMAC, not a JWT, so it cannot be used as an access token.
 */
@Component
public class UnsubscribeTokens {

    private static final String DOMAIN = "itjobs-unsubscribe:";
    private final byte[] key;

    public UnsubscribeTokens(@Value("${hoidanit.jwt.base64-secret}") String secret) {
        this.key = Hmac.key(secret);
    }

    public String create(long subscriberId) {
        return subscriberId + "." + mac(subscriberId);
    }

    public OptionalLong parse(String token) {
        if (token == null) {
            return OptionalLong.empty();
        }
        int dot = token.indexOf('.');
        if (dot < 1 || dot == token.length() - 1) {
            return OptionalLong.empty();
        }
        try {
            long id = Long.parseLong(token.substring(0, dot));
            byte[] given = token.substring(dot + 1).getBytes(StandardCharsets.UTF_8);
            byte[] expected = mac(id).getBytes(StandardCharsets.UTF_8);
            return id > 0 && MessageDigest.isEqual(given, expected) ? OptionalLong.of(id) : OptionalLong.empty();
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    private String mac(long subscriberId) {
        return Hmac.sign(this.key, DOMAIN + subscriberId);
    }
}
