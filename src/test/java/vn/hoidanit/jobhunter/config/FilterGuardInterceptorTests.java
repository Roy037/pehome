package vn.hoidanit.jobhunter.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import vn.hoidanit.jobhunter.util.error.IdInvalidException;

class FilterGuardInterceptorTests {
    private final FilterGuardInterceptor guard = new FilterGuardInterceptor();

    private boolean allows(String param, String value) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter(param, value);
        return guard.preHandle(request, new MockHttpServletResponse(), new Object());
    }

    @Test
    void refusesExpressionsThatReachSecrets() {
        assertThrows(IdInvalidException.class, () -> allows("filter", "password ~ 'a'"));
        assertThrows(IdInvalidException.class, () -> allows("filter", "company.users.refreshToken ~ 'eyJ'"));
        assertThrows(IdInvalidException.class, () -> allows("filter", "user.tokenHash : 'x'"));
        assertThrows(IdInvalidException.class, () -> allows("filter", "PASSWORD ~ 'a'"), "case-insensitive");
        assertThrows(IdInvalidException.class, () -> allows("sort", "user.password,asc"));
        assertThrows(IdInvalidException.class, () -> allows("filter", "name ~ 'x' and users.id > 0"));
    }

    @Test
    void allowsOrdinaryFiltersAndWordsInsideStringLiterals() throws Exception {
        assertTrue(allows("filter", "active : true and salary > 1000"));
        assertTrue(allows("filter", "name ~ 'password manager developer'"), "searching for the word is fine");
        assertTrue(allows("filter", "name ~ 'it\\'s a password' and locked : false"), "escaped quote inside a literal");
        assertTrue(allows("sort", "createdAt,desc"));
        assertTrue(allows("page", "1"), "other parameters are not inspected");
    }
}
