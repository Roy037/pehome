package vn.hoidanit.jobhunter.config;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.hoidanit.jobhunter.domain.Permission;
import vn.hoidanit.jobhunter.domain.Role;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.service.UserService;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.error.PermissionException;

public class PermissionInterceptor implements HandlerInterceptor {

    // Any signed-in user may manage their own job-alert subscription and apply for jobs; everything else is permission-checked.
    private static final Set<String> SELF_SERVICE = Set.of(
            "POST /api/v1/subscribers", "PUT /api/v1/subscribers", "POST /api/v1/subscribers/skills",
            // a signed-in user may also follow the unsubscribe link from their e-mail
            "POST /api/v1/subscribers/unsubscribe",
            "POST /api/v1/resumes", "POST /api/v1/resumes/by-user",
            // authorised inside the controller (owner, the job's company, or SUPER_ADMIN)
            "GET /api/v1/resumes/{id}/document", "GET /api/v1/resumes/check-applied");

    // Reading jobs, companies and skills is public; writing them needs a role permission.
    private static final List<String> PUBLIC_READ = List.of("/api/v1/jobs", "/api/v1/companies", "/api/v1/skills");

    @Autowired
    UserService userService;

    @Override
    @Transactional
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response, Object handler)
            throws Exception {

        String path = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String httpMethod = request.getMethod();
        if (SELF_SERVICE.contains(httpMethod + " " + path)
                || ("GET".equals(httpMethod) && path != null && PUBLIC_READ.stream().anyMatch(path::startsWith))) {
            return true;
        }

        // check permission
        String email = SecurityUtil.getCurrentUserLogin().isPresent() == true
                ? SecurityUtil.getCurrentUserLogin().get()
                : "";
        if (email != null && !email.isEmpty()) {
            User user = this.userService.handleGetUserByUsername(email);
            if (user != null) {
                Role role = user.getRole();
                if (role != null) {
                    List<Permission> permissions = role.getPermissions();
                    boolean isAllow = permissions.stream().anyMatch(item -> item.getApiPath().equals(path)
                            && item.getMethod().equals(httpMethod));

                    if (isAllow == false) {
                        throw new PermissionException("Bạn không có quyền truy cập endpoint này.");
                    }
                } else {
                    throw new PermissionException("Bạn không có quyền truy cập endpoint này.");
                }
            }
        }

        return true;
    }
}