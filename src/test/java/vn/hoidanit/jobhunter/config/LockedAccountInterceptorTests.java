package vn.hoidanit.jobhunter.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.repository.UserRepository;

class LockedAccountInterceptorTests {
    UserRepository users = mock(UserRepository.class);
    LockedAccountInterceptor interceptor = new LockedAccountInterceptor(users);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    boolean requestAs(String email) {
        if (email != null) {
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(email, null));
        }
        return interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());
    }

    @Test
    void aLockedAccountIsRefusedOnTheNextRequestEvenWithAValidToken() {
        User user = new User();
        user.setLocked(true);
        when(users.findByEmail("an@example.com")).thenReturn(user);

        assertThrows(BadCredentialsException.class, () -> requestAs("an@example.com"));

        user.setLocked(false);
        assertTrue(requestAs("an@example.com"));
    }

    @Test
    void anonymousRequestsAreNotLookedUp() {
        assertTrue(requestAs(null));

        verifyNoInteractions(users);
    }
}
