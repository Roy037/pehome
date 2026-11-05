package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import vn.hoidanit.jobhunter.domain.User;

class UserDetailCustomTests {

    @Test
    void anUnknownEmailFailsLikeAWrongPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        User known = new User();
        known.setEmail("a@example.com");
        known.setPassword(encoder.encode("right-password"));
        UserService users = mock(UserService.class);
        when(users.handleGetUserByUsername("a@example.com")).thenReturn(known);

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(new UserDetailCustom(users));
        provider.setPasswordEncoder(encoder);

        BadCredentialsException wrongPassword = assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(new UsernamePasswordAuthenticationToken("a@example.com", "wrong")));
        BadCredentialsException unknownEmail = assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(new UsernamePasswordAuthenticationToken("nobody@example.com", "wrong")));
        assertEquals(wrongPassword.getMessage(), unknownEmail.getMessage());
    }
}
