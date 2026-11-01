package vn.hoidanit.jobhunter.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Role;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.UserIdentity;
import vn.hoidanit.jobhunter.oauth.OAuthProvider.Profile;
import vn.hoidanit.jobhunter.repository.UserIdentityRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.service.NotificationService;
import vn.hoidanit.jobhunter.service.RoleService;

class OAuthAccountServiceTests {
    UserRepository users = mock(UserRepository.class);
    UserIdentityRepository identities = mock(UserIdentityRepository.class);
    RoleService roles = mock(RoleService.class);
    NotificationService notifications = mock(NotificationService.class);
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    OAuthAccountService service = new OAuthAccountService(users, identities, roles, encoder, notifications);
    Role candidate = new Role();

    @BeforeEach
    void setUp() {
        candidate.setName("NORMAL_USER");
        when(roles.fetchByName("NORMAL_USER")).thenReturn(candidate);
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        when(identities.findByProviderAndProviderUserId(any(), any())).thenReturn(Optional.empty());
    }

    Profile profile(String email, boolean verified) {
        return new Profile(OAuthProvider.GOOGLE, "sub-1", email, verified, "Ann Nguyen");
    }

    User existing(boolean verified) {
        User user = new User();
        user.setId(5);
        user.setEmail("ann@example.com");
        user.setPassword(encoder.encode("attacker-chosen"));
        user.setRefreshToken("old-refresh");
        user.setRole(candidate);
        user.setEmailVerified(verified);
        when(users.findByEmail("ann@example.com")).thenReturn(user);
        return user;
    }

    @Test
    void aLinkedAccountSignsStraightIn() {
        User user = existing(true);
        UserIdentity identity = new UserIdentity();
        identity.setUser(user);
        when(identities.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "sub-1")).thenReturn(Optional.of(identity));
        assertSame(user, service.signIn(profile("other@example.com", true)), "the link wins over whatever e-mail the provider shows today");
        verify(identities, never()).save(any());
    }

    @Test
    void aNewVisitorGetsAVerifiedCandidateAccountAndAWelcome() {
        User created = service.signIn(profile("Ann@Example.com", true));
        assertEquals("ann@example.com", created.getEmail(), "e-mails are stored lower-case");
        assertEquals("Ann Nguyen", created.getName());
        assertTrue(created.isEmailVerified());
        assertSame(candidate, created.getRole());
        assertNull(created.getCompany());
        assertNotEquals("", created.getPassword(), "an unguessable password, so password log-in stays closed");
        ArgumentCaptor<UserIdentity> identity = ArgumentCaptor.forClass(UserIdentity.class);
        verify(identities).save(identity.capture());
        assertEquals("sub-1", identity.getValue().getProviderUserId());
        verify(notifications).welcomeSocial(created, OAuthProvider.GOOGLE);
    }

    @Test
    void aVerifiedExistingAccountIsLinkedAndKeepsItsPassword() {
        User user = existing(true);
        String password = user.getPassword();
        assertSame(user, service.signIn(profile("ann@example.com", true)));
        assertEquals(password, user.getPassword());
        verify(identities).save(any(UserIdentity.class));
        verify(notifications, never()).welcomeSocial(any(), any());
    }

    @Test
    void anUnprovenExistingAccountLosesItsPasswordBeforeTheLinkIsMade() {
        User user = existing(false);
        String password = user.getPassword();
        service.signIn(profile("ann@example.com", true));
        assertNotEquals(password, user.getPassword(), "whoever pre-registered the address cannot keep a way in");
        assertFalse(encoder.matches("attacker-chosen", user.getPassword()));
        assertNull(user.getRefreshToken());
        assertTrue(user.isEmailVerified());
    }

    @Test
    void thingsThatCannotSignInAreRefusedWithAReason() {
        assertEquals(OAuthException.NO_EMAIL, assertThrows(OAuthException.class, () -> service.signIn(profile(null, false))).code());
        assertEquals(OAuthException.UNVERIFIED_EMAIL, assertThrows(OAuthException.class, () -> service.signIn(profile("ann@example.com", false))).code());

        User locked = existing(true);
        locked.setLocked(true);
        assertEquals(OAuthException.LOCKED, assertThrows(OAuthException.class, () -> service.signIn(profile("ann@example.com", true))).code());

        locked.setLocked(false);
        locked.setCompany(new Company());
        assertEquals(OAuthException.UNSUPPORTED_ACCOUNT, assertThrows(OAuthException.class, () -> service.signIn(profile("ann@example.com", true))).code(), "employer");

        locked.setCompany(null);
        Role admin = new Role();
        admin.setName("SUPER_ADMIN");
        locked.setRole(admin);
        assertEquals(OAuthException.UNSUPPORTED_ACCOUNT, assertThrows(OAuthException.class, () -> service.signIn(profile("ann@example.com", true))).code(), "admin");
        verify(identities, never()).save(any());
    }
}
