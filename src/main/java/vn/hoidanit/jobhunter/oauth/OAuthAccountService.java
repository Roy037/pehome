package vn.hoidanit.jobhunter.oauth;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.UserIdentity;
import vn.hoidanit.jobhunter.oauth.OAuthProvider.Profile;
import vn.hoidanit.jobhunter.repository.UserIdentityRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.service.NotificationService;
import vn.hoidanit.jobhunter.service.RoleService;

/**
 * Turns "this Google / Facebook / LinkedIn account signed in" into an itjobs user:
 * <ol>
 * <li>the external account is already linked: that user;</li>
 * <li>an itjobs account with the same (provider-verified) e-mail exists: link to it. If that account never proved its address
 * its password is replaced first, so whoever pre-registered someone else's e-mail cannot keep a way in;</li>
 * <li>otherwise a new candidate account is created.</li>
 * </ol>
 * Only candidate accounts sign in this way; employers and admins keep their passwords.
 */
@Service
public class OAuthAccountService {

    private final SecureRandom random = new SecureRandom();
    private final UserRepository userRepository;
    private final UserIdentityRepository identityRepository;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    public OAuthAccountService(UserRepository userRepository, UserIdentityRepository identityRepository, RoleService roleService,
            PasswordEncoder passwordEncoder, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
    }

    @Transactional
    public User signIn(Profile profile) {
        UserIdentity linked = this.identityRepository.findByProviderAndProviderUserId(profile.provider(), profile.subject()).orElse(null);
        if (linked != null) {
            return eligible(linked.getUser());
        }

        String email = profile.email() == null ? null : profile.email().trim().toLowerCase();
        if (email == null || email.isEmpty()) {
            throw new OAuthException(OAuthException.NO_EMAIL, profile.provider() + " account has no e-mail address");
        }
        if (!profile.emailVerified()) {
            throw new OAuthException(OAuthException.UNVERIFIED_EMAIL, profile.provider() + " has not verified " + email);
        }

        User user = this.userRepository.findByEmail(email);
        if (user != null) {
            eligible(user);
            if (!user.isEmailVerified()) {
                user.setPassword(this.passwordEncoder.encode(randomSecret()));
                user.setRefreshToken(null);
                user.setEmailVerified(true);
                user = this.userRepository.save(user);
            }
        } else {
            user = createCandidate(profile, email);
        }
        link(user, profile);
        return user;
    }

    private User createCandidate(Profile profile, String email) {
        User user = new User();
        user.setEmail(email);
        user.setName(profile.name() == null ? email.substring(0, email.indexOf('@')) : profile.name());
        user.setPassword(this.passwordEncoder.encode(randomSecret())); // nobody knows it: the account is reached through the provider or a password reset
        user.setRole(this.roleService.fetchByName("NORMAL_USER"));
        user.setCompany(null);
        user.setLocked(false);
        user.setEmailVerified(true);
        user = this.userRepository.save(user);
        this.notificationService.welcomeSocial(user, profile.provider());
        return user;
    }

    private void link(User user, Profile profile) {
        UserIdentity identity = new UserIdentity();
        identity.setUser(user);
        identity.setProvider(profile.provider());
        identity.setProviderUserId(profile.subject());
        this.identityRepository.save(identity);
    }

    private static User eligible(User user) {
        if (user.isLocked()) {
            throw new OAuthException(OAuthException.LOCKED, "account " + user.getId() + " is locked");
        }
        boolean candidate = user.getCompany() == null && user.getRole() != null && "NORMAL_USER".equals(user.getRole().getName());
        if (!candidate) {
            throw new OAuthException(OAuthException.UNSUPPORTED_ACCOUNT, "account " + user.getId() + " is not a candidate account");
        }
        return user;
    }

    private String randomSecret() {
        byte[] bytes = new byte[32];
        this.random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
