package vn.hoidanit.jobhunter.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class SecurityUtilTests {
    @Test
    void refreshTokensAreStoredAsSha256Hex() {
        // FIPS 180-2 test vector
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", SecurityUtil.sha256("abc"));
        assertEquals(64, SecurityUtil.sha256("eyJhbGciOiJIUzUxMiJ9.payload.signature").length());
        assertNotEquals(SecurityUtil.sha256("token-a"), SecurityUtil.sha256("token-b"));
        assertEquals(SecurityUtil.sha256("same"), SecurityUtil.sha256("same"));
    }
}
