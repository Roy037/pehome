package vn.hoidanit.jobhunter.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class OAuthStateTests {
    static final String SECRET = Base64.getEncoder().encodeToString("an-oauth-state-test-secret-0123456789-0123456789".getBytes());
    final OAuthState state = new OAuthState(SECRET);

    @Test
    void aStateComesBackWithTheSameBrowserAndTheChosenPage() {
        String nonce = state.newNonce();
        assertEquals("/job/7?tab=1", state.verify(state.create(nonce, "/job/7?tab=1"), nonce).orElseThrow());
    }

    @Test
    void aDifferentBrowserOrATamperedStateIsRefused() {
        String nonce = state.newNonce();
        String value = state.create(nonce, "/ho-so");
        assertTrue(state.verify(value, state.newNonce()).isEmpty(), "cookie nonce of another browser");
        assertTrue(state.verify(value, null).isEmpty(), "no cookie at all");
        String[] parts = value.split("\\.");
        String otherNext = Base64.getUrlEncoder().withoutPadding().encodeToString("/admin".getBytes());
        assertTrue(state.verify(parts[0] + "." + parts[1] + "." + otherNext + "." + parts[3], nonce).isEmpty(), "next swapped");
        assertTrue(state.verify(parts[0] + ".9999999999." + parts[2] + "." + parts[3], nonce).isEmpty(), "expiry extended");
        assertTrue(state.verify(value + "x", nonce).isEmpty(), "signature changed");
        assertTrue(new OAuthState(Base64.getEncoder().encodeToString("some-other-secret-0123456789-0123456789-01234".getBytes())).verify(value, nonce).isEmpty(),
                "signed with another secret");
    }

    @Test
    void anExpiredStateIsRefused() {
        String nonce = state.newNonce();
        assertTrue(state.verify(state.create(nonce, "/", Instant.now().minusSeconds(5)), nonce).isEmpty());
    }

    @Test
    void garbageNeverThrows() {
        for (String junk : new String[] { "", "a", "a.b", "a.b.c", "a.b.c.d", "....", "a.1.%%%.d" }) {
            assertTrue(state.verify(junk, "a").isEmpty(), junk);
        }
    }

    @Test
    void onlyPathsOnThisSiteAreAcceptedAsTheLandingPage() {
        assertEquals("/job/7", OAuthState.safeNext("/job/7"));
        assertEquals("/job?skills=1,2", OAuthState.safeNext("/job?skills=1,2"));
        for (String bad : new String[] { null, "", "job/7", "//evil.com", "https://evil.com", "/\\evil.com", "/a b", "/<script>", "javascript:alert(1)", "/" + "a".repeat(250) }) {
            assertEquals("/", OAuthState.safeNext(bad), String.valueOf(bad));
        }
    }
}
