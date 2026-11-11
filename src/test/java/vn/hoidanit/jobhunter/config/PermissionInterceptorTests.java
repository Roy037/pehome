package vn.hoidanit.jobhunter.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerMapping;

import vn.hoidanit.jobhunter.domain.Permission;
import vn.hoidanit.jobhunter.domain.Role;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.service.UserService;
import vn.hoidanit.jobhunter.util.error.PermissionException;

class PermissionInterceptorTests {
    PermissionInterceptor interceptor = new PermissionInterceptor();
    UserService users = mock(UserService.class);
    Role role = new Role();
    User hr = new User();

    static Permission permission(String method, String path) {
        Permission permission = new Permission();
        permission.setMethod(method);
        permission.setApiPath(path);
        return permission;
    }

    @BeforeEach
    void setUp() {
        interceptor.userService = users;
        role.setName("HR");
        role.setPermissions(new ArrayList<>(List.of(permission("GET", "/api/v1/resumes"))));
        hr.setEmail("hr@example.com");
        hr.setRole(role);
        when(users.handleGetUserByUsername("hr@example.com")).thenReturn(hr);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("hr@example.com", null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    boolean call(String method, String pattern) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, pattern);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, pattern);
        return interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
    }

    @Test
    void aRoleReachesOnlyTheEndpointsItHasAPermissionFor() throws Exception {
        assertTrue(call("GET", "/api/v1/resumes"));
        assertThrows(PermissionException.class, () -> call("DELETE", "/api/v1/resumes/{id}"));
        assertThrows(PermissionException.class, () -> call("GET", "/api/v1/users"));
    }

    @Test
    void aRemovedPermissionStopsTheVeryNextRequest() throws Exception {
        assertTrue(call("GET", "/api/v1/resumes"));

        role.getPermissions().clear();

        assertThrows(PermissionException.class, () -> call("GET", "/api/v1/resumes"));
    }

    @Test
    void applyingAndPublicReadsNeedNoPermission() throws Exception {
        assertTrue(call("POST", "/api/v1/resumes"));
        assertTrue(call("POST", "/api/v1/subscribers"));
        assertTrue(call("GET", "/api/v1/jobs/{id}"));
        assertTrue(call("GET", "/api/v1/companies"));
        assertThrows(PermissionException.class, () -> call("POST", "/api/v1/jobs"));
    }

    @Test
    void anAccountWithoutARoleIsRefused() {
        hr.setRole(null);

        assertThrows(PermissionException.class, () -> call("GET", "/api/v1/resumes"));
    }
}
