package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import vn.hoidanit.jobhunter.util.error.TooManyRequestsException;

class LoginThrottleTests {
    private static void fail(LoginThrottle throttle, String user, int times) {
        for (int i = 0; i < times; i++) {
            throttle.failed(user);
        }
    }

    @Test
    void locksAnAccountAfterTooManyWrongPasswords() {
        LoginThrottle throttle = new LoginThrottle();
        fail(throttle, "a@example.com", LoginThrottle.MAX_FAILURES - 1);
        assertDoesNotThrow(() -> throttle.check("a@example.com"));
        throttle.failed("a@example.com");
        assertThrows(TooManyRequestsException.class, () -> throttle.check("a@example.com"));
    }

    @Test
    void doesNotAffectOtherAccountsAndIgnoresCaseAndSpaces() {
        LoginThrottle throttle = new LoginThrottle();
        fail(throttle, " A@Example.com ", LoginThrottle.MAX_FAILURES);
        assertThrows(TooManyRequestsException.class, () -> throttle.check("a@example.com"));
        assertDoesNotThrow(() -> throttle.check("b@example.com"));
    }

    @Test
    void aSuccessfulLoginClearsTheCount() {
        LoginThrottle throttle = new LoginThrottle();
        fail(throttle, "a@example.com", LoginThrottle.MAX_FAILURES - 1);
        throttle.succeeded("a@example.com");
        fail(throttle, "a@example.com", LoginThrottle.MAX_FAILURES - 1);
        assertDoesNotThrow(() -> throttle.check("a@example.com"));
    }
}
