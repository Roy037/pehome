package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class UnsubscribeTokensTests {
    static final String SECRET = Base64.getEncoder().encodeToString("a-test-secret-that-is-long-enough-for-hmac-0123456789".getBytes());
    final UnsubscribeTokens tokens = new UnsubscribeTokens(SECRET);

    @Test
    void aSignedTokenFindsItsSubscriber() {
        assertEquals(42, tokens.parse(tokens.create(42)).getAsLong());
    }

    @Test
    void tamperedOrForeignTokensAreRejected() {
        String token = tokens.create(42);
        String mac = token.substring(token.indexOf('.') + 1);
        assertTrue(tokens.parse("43." + mac).isEmpty(), "another subscriber's id with this signature");
        assertTrue(tokens.parse("42." + mac.substring(1) + "x").isEmpty(), "altered signature");
        assertTrue(new UnsubscribeTokens(Base64.getEncoder().encodeToString("some-other-secret-0123456789-0123456789-0123456789".getBytes())).parse(token).isEmpty(),
                "signed with a different secret");
    }

    @Test
    void garbageIsRejectedWithoutThrowing() {
        for (String junk : new String[] { null, "", ".", "42", "42.", ".abc", "abc.def", "-5.abc", "9999999999999999999999.abc" }) {
            assertTrue(tokens.parse(junk).isEmpty(), String.valueOf(junk));
        }
    }
}
