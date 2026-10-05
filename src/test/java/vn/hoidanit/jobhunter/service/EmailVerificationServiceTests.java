package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import vn.hoidanit.jobhunter.domain.EmailVerificationToken;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.repository.EmailVerificationTokenRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.error.ConflictException;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import vn.hoidanit.jobhunter.util.error.PermissionException;
import vn.hoidanit.jobhunter.util.error.TooManyRequestsException;

class EmailVerificationServiceTests {
    EmailVerificationTokenRepository tokens = mock(EmailVerificationTokenRepository.class);
    UserRepository users = mock(UserRepository.class);
    NotificationService notifications = mock(NotificationService.class);
    EmailVerificationService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(tokens, users, notifications);
        user = new User();
        user.setId(7);
        user.setEmail("a@example.com");
        user.setEmailVerified(false);
    }

    EmailVerificationToken row(String token, Duration validFor, boolean used) {
        EmailVerificationToken row = new EmailVerificationToken();
        row.setTokenHash(SecurityUtil.sha256(token));
        row.setUser(user);
        row.setExpiryDate(Instant.now().plus(validFor));
        row.setUsed(used);
        when(tokens.findByTokenHash(SecurityUtil.sha256(token))).thenReturn(Optional.of(row));
        return row;
    }

    @Test
    void aValidLinkVerifiesTheUserAndRetiresOldLinks() throws Exception {
        row("good", Duration.ofHours(1), false);
        service.verify("good");
        assertTrue(user.isEmailVerified());
        verify(users).save(user);
        verify(tokens).invalidateAllFor(7);
    }

    @Test
    void expiredUsedOrUnknownLinksAreRefused() {
        row("old", Duration.ofMinutes(-1), false);
        row("spent", Duration.ofHours(1), true);
        assertThrows(IdInvalidException.class, () -> service.verify("old"));
        assertThrows(IdInvalidException.class, () -> service.verify("spent"));
        assertThrows(IdInvalidException.class, () -> service.verify("never-issued"));
        assertFalse(user.isEmailVerified());
        verify(users, never()).save(any());
    }

    @Test
    void openingTheLinkTwiceStillSucceeds() {
        row("spent", Duration.ofHours(1), true);
        user.setEmailVerified(true);
        assertDoesNotThrow(() -> service.verify("spent"));
    }

    @Test
    void theE_mailLinkCarriesAFreshTokenWhoseHashIsStored() {
        service.sendWelcome(user);
        ArgumentCaptor<EmailVerificationToken> saved = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokens).save(saved.capture());
        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(notifications).welcome(any(), path.capture());
        String token = path.getValue().substring(path.getValue().indexOf("token=") + 6);
        assertTrue(path.getValue().startsWith("/xac-thuc-email?token="));
        assertTrue(saved.getValue().getTokenHash().equals(SecurityUtil.sha256(token)), "only the hash is stored");
        verify(tokens).invalidateAllFor(7);
    }

    @Test
    void resendRefusesVerifiedUsersAndRapidRepeats() {
        EmailVerificationToken recent = new EmailVerificationToken();
        recent.setCreatedAt(Instant.now().minusSeconds(10));
        when(tokens.findFirstByUserIdOrderByCreatedAtDesc(7)).thenReturn(Optional.of(recent));
        assertThrows(TooManyRequestsException.class, () -> service.resend(user));
        verify(notifications, never()).verifyEmail(any(), anyString());

        recent.setCreatedAt(Instant.now().minusSeconds(120));
        service.resend(user);
        verify(notifications).verifyEmail(any(), startsWith("/xac-thuc-email?token="));

        user.setEmailVerified(true);
        assertThrows(ConflictException.class, () -> service.resend(user));
    }

    @Test
    void applyingAndSubscribingWaitForAVerifiedAddress() throws Exception {
        assertThrows(PermissionException.class, () -> EmailVerificationService.requireVerified(user));
        user.setEmailVerified(true);
        EmailVerificationService.requireVerified(user);
    }
}
