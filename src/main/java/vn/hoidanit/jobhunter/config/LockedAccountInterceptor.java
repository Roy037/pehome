package vn.hoidanit.jobhunter.config;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.util.SecurityUtil;

/**
 * Access tokens live 30 minutes, so locking an account also has to refuse the token it already holds.
 * One indexed lookup per signed-in request; cache by e-mail if that ever shows up in a profile.
 */
public class LockedAccountInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;

    public LockedAccountInterceptor(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String email = SecurityUtil.getCurrentUserLogin().orElse("");
        if (!email.isEmpty()) {
            User user = this.userRepository.findByEmail(email);
            if (user != null && user.isLocked()) {
                throw new BadCredentialsException("Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.");
            }
        }
        return true;
    }
}
