package vn.hoidanit.jobhunter.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hoidanit.jobhunter.domain.EmailVerificationToken;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.repository.EmailVerificationTokenRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.error.ConflictException;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import vn.hoidanit.jobhunter.util.error.PermissionException;
import vn.hoidanit.jobhunter.util.error.TooManyRequestsException;

/**
 * Proves that someone who signs up really owns the address. The sign-up e-mail carries the link (it doubles as the welcome
 * e-mail); an unverified candidate can browse but cannot apply or subscribe, the two things that send mail to that address.
 */
@Service
public class EmailVerificationService {

    static final Duration TTL = Duration.ofHours(48);
    static final Duration RESEND_GAP = Duration.ofMinutes(1);

    private final SecureRandom random = new SecureRandom();
    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public EmailVerificationService(EmailVerificationTokenRepository tokenRepository, UserRepository userRepository,
            NotificationService notificationService) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /** Candidate sign-up: the welcome e-mail with the verification button. */
    @Transactional
    public void sendWelcome(User user) {
        this.notificationService.welcome(user, issue(user));
    }

    /** Employer sign-up: "we got your registration" with the verification button. */
    @Transactional
    public void sendEmployerWelcome(User employer) {
        this.notificationService.employerRegistered(employer, issue(employer));
    }

    /** "Send it again" from the banner. */
    @Transactional
    public void resend(User user) {
        if (user.isEmailVerified()) {
            throw new ConflictException("Email của bạn đã được xác thực rồi.");
        }
        EmailVerificationToken latest = this.tokenRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId()).orElse(null);
        if (latest != null && !latest.isUsed() && latest.getCreatedAt().isAfter(Instant.now().minus(RESEND_GAP))) {
            throw new TooManyRequestsException("Vui lòng đợi một phút rồi gửi lại email xác thực.");
        }
        this.notificationService.verifyEmail(user, issue(user));
    }

    /** Opening the link twice (or a mail client pre-fetching it) is harmless: a used link of a verified user still succeeds. */
    @Transactional
    public void verify(String token) throws IdInvalidException {
        EmailVerificationToken row = this.tokenRepository.findByTokenHash(SecurityUtil.sha256(token))
                .orElseThrow(EmailVerificationService::invalid);
        User user = row.getUser();
        if (row.isUsed()) {
            if (user.isEmailVerified()) {
                return;
            }
            throw invalid();
        }
        if (row.getExpiryDate().isBefore(Instant.now())) {
            throw invalid();
        }
        user.setEmailVerified(true);
        this.userRepository.save(user);
        this.tokenRepository.invalidateAllFor(user.getId());
    }

    /** Candidate actions that e-mail the account (apply, subscribe) wait until the address is proven. */
    public static void requireVerified(User user) throws PermissionException {
        if (!user.isEmailVerified()) {
            throw new PermissionException(
                    "Vui lòng xác thực email trước khi tiếp tục. Kiểm tra hộp thư của bạn hoặc bấm “Gửi lại” ở đầu trang.");
        }
    }

    // Returns the path (with the token) the e-mail button should open.
    private String issue(User user) {
        this.tokenRepository.invalidateAllFor(user.getId());
        byte[] bytes = new byte[32];
        this.random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        EmailVerificationToken row = new EmailVerificationToken();
        row.setTokenHash(SecurityUtil.sha256(token));
        row.setUser(user);
        row.setExpiryDate(Instant.now().plus(TTL));
        this.tokenRepository.save(row);
        return "/xac-thuc-email?token=" + token;
    }

    private static IdInvalidException invalid() {
        return new IdInvalidException("Liên kết xác thực không hợp lệ hoặc đã hết hạn.");
    }
}
