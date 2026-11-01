package vn.hoidanit.jobhunter.util;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-SHA256 helpers. Callers supply key bytes; payment keys are UTF-8, not decoded JWT secrets. */
public final class Hmac {
    private Hmac() {
    }

    /** The configured secret is base64; a value that is not valid base64 is taken as plain text. A blank secret is dev-only. */
    public static byte[] key(String secret) {
        String value = secret == null || secret.isBlank() ? "hmac-dev-only" : secret.trim();
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
    }

    /** base64url (no padding) of the MAC of the message. */
    public static String sign(byte[] key, String message) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest(key, message));
    }

    public static String signHex(byte[] key, String message) {
        return HexFormat.of().formatHex(digest(key, message));
    }

    private static byte[] digest(byte[] key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
