package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import vn.hoidanit.jobhunter.domain.PasswordResetToken;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.repository.PasswordResetTokenRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

class PasswordResetTests {
    PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    UserRepository users = mock(UserRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    EmailService email = mock(EmailService.class);
    NotificationService notifications = mock(NotificationService.class);
    PasswordResetService service = new PasswordResetService(tokens, users, encoder, email, notifications);
    User user = new User();

    @BeforeEach
    void setUp() {
        user.setId(7);
        user.setEmail("an@example.com");
        user.setRefreshToken("hash-of-an-old-session");
        user.setEmailVerified(false);
        when(users.findByEmail("an@example.com")).thenReturn(user);
        when(tokens.findFirstByUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.empty());
        when(encoder.encode(anyString())).thenAnswer(call -> "encoded:" + call.getArgument(0));
    }

    @SuppressWarnings("unchecked")
    String requestLinkAndReadTokenFromMail() {
        service.requestReset("  an@example.com ");
        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        verify(email).sendTemplate(eq("an@example.com"), anyString(), eq("reset-password"), vars.capture());
        String url = (String) vars.getValue().get("resetUrl");
        return url.substring(url.indexOf("token=") + "token=".length());
    }

    PasswordResetToken savedRow() {
        ArgumentCaptor<PasswordResetToken> row = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(row.capture());
        return row.getValue();
    }

    @Test
    void anUnknownAddressGetsNoMailAndNoHint() {
        assertDoesNotThrow(() -> service.requestReset("nobody@example.com"));

        verify(tokens, never()).save(any());
        verifyNoInteractions(email);
    }

    @Test
    void onlyTheHashOfTheLinkIsStoredAndItLastsFifteenMinutes() {
        String token = requestLinkAndReadTokenFromMail();
        PasswordResetToken row = savedRow();

        assertNotEquals(token, row.getTokenHash());
        assertEquals(SecurityUtil.sha256(token), row.getTokenHash());
        Duration life = Duration.between(Instant.now(), row.getExpiryDate());
        assertTrue(life.compareTo(Duration.ofMinutes(14)) > 0 && life.compareTo(Duration.ofMinutes(15)) <= 0, life.toString());
        verify(tokens).invalidateAllFor(7L);
    }

    @Test
    void askingAgainWithinAMinuteSendsNothing() {
        PasswordResetToken recent = new PasswordResetToken();
        recent.setCreatedAt(Instant.now().minusSeconds(20));
        when(tokens.findFirstByUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.of(recent));

        service.requestReset("an@example.com");
        verifyNoInteractions(email);

        recent.setCreatedAt(Instant.now().minus(Duration.ofMinutes(2)));
        service.requestReset("an@example.com");
        verify(email).sendTemplate(eq("an@example.com"), anyString(), eq("reset-password"), anyMap());
    }

    @Test
    void theLinkSetsTheNewPasswordOnceAndEndsOldSessions() throws Exception {
        String token = requestLinkAndReadTokenFromMail();
        PasswordResetToken row = savedRow();
        when(tokens.findByTokenHash(row.getTokenHash())).thenReturn(Optional.of(row));

        service.resetPassword(token, "MatKhauMoi1");

        assertEquals("encoded:MatKhauMoi1", user.getPassword());
        assertNull(user.getRefreshToken());
        assertTrue(user.isEmailVerified());
        verify(notifications).passwordChanged(user);

        row.setUsed(true);
        assertThrows(IdInvalidException.class, () -> service.resetPassword(token, "MatKhauKhac2"));
    }

    @Test
    void anExpiredOrUnknownLinkIsRefused() {
        PasswordResetToken expired = new PasswordResetToken();
        expired.setUser(user);
        expired.setExpiryDate(Instant.now().minusSeconds(1));
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(expired));
        assertThrows(IdInvalidException.class, () -> service.resetPassword("any-token", "MatKhauMoi1"));

        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertThrows(IdInvalidException.class, () -> service.resetPassword("any-token", "MatKhauMoi1"));

        verify(users, never()).save(any());
    }
}
