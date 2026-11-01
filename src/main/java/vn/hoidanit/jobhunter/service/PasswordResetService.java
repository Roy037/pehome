package vn.hoidanit.jobhunter.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hoidanit.jobhunter.domain.PasswordResetToken;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.repository.PasswordResetTokenRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

@Service
public class PasswordResetService {

    static final Duration TTL = Duration.ofMinutes(15);
    // asking again within this window sends nothing, so the form cannot be used to flood someone's inbox
    static final Duration RESEND_GAP = Duration.ofMinutes(1);

    private final SecureRandom random = new SecureRandom();
    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final NotificationService notificationService;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository, UserRepository userRepository,
            PasswordEncoder passwordEncoder, EmailService emailService, NotificationService notificationService) {
        this.notificationService = notificationService;
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    /** Never reveals whether the address is registered: the caller answers the same way either way. */
    @Transactional
    public void requestReset(String email) {
        User user = this.userRepository.findByEmail(email.trim());
        if (user == null) {
            return;
        }
        Optional<PasswordResetToken> latest = this.tokenRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
        if (latest.isPresent() && !latest.get().isUsed()
                && latest.get().getCreatedAt().isAfter(Instant.now().minus(RESEND_GAP))) {
            return;
        }
        this.tokenRepository.invalidateAllFor(user.getId());

        byte[] bytes = new byte[32];
        this.random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        PasswordResetToken row = new PasswordResetToken();
        row.setTokenHash(hash(token));
        row.setUser(user);
        row.setExpiryDate(Instant.now().plus(TTL));
        this.tokenRepository.save(row);

        this.emailService.sendTemplate(user.getEmail(), "Đặt lại mật khẩu itjobs", "reset-password", Map.of(
                "name", user.getName() == null ? "bạn" : user.getName(),
                "resetUrl", this.frontendUrl + "/reset-password?token=" + token,
                "minutes", TTL.toMinutes()));
    }

    @Transactional
    public void resetPassword(String token, String newPassword) throws IdInvalidException {
        PasswordResetToken row = this.tokenRepository.findByTokenHash(hash(token))
                .orElseThrow(PasswordResetService::invalid);
        if (row.isUsed() || row.getExpiryDate().isBefore(Instant.now())) {
            throw invalid();
        }
        User user = row.getUser();
        user.setPassword(this.passwordEncoder.encode(newPassword));
        user.setRefreshToken(null); // sessions opened with the old password can no longer be renewed
        user.setEmailVerified(true); // the reset link only reached the owner of the address
        this.userRepository.save(user);
        this.tokenRepository.invalidateAllFor(user.getId());
        this.notificationService.passwordChanged(user);
    }

    private static IdInvalidException invalid() {
        return new IdInvalidException("Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.");
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
