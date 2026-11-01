package vn.hoidanit.jobhunter.config;

import java.util.regex.Pattern;

import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

/**
 * The list endpoints accept a free-form spring-filter expression and sort, which can walk entity relations
 * (e.g. {@code company.users.refreshToken ~ 'eyJ'}). Used as a yes/no question it would leak password hashes and live
 * refresh tokens one character at a time, so expressions that name those fields (or the company -> users relation)
 * are refused before they reach a controller. String literals are ignored, so searching for the word "password" in a
 * job title still works.
 */
public class FilterGuardInterceptor implements HandlerInterceptor {

    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:\\\\.|[^'\\\\])*'");
    private static final Pattern FORBIDDEN = Pattern.compile("(?i)\\b(password|refreshToken|tokenHash|users)\\b");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        for (String name : new String[] { "filter", "sort" }) {
            String[] values = request.getParameterValues(name);
            if (values == null) {
                continue;
            }
            for (String value : values) {
                String identifiersOnly = STRING_LITERAL.matcher(value).replaceAll("''");
                if (FORBIDDEN.matcher(identifiersOnly).find()) {
                    throw new IdInvalidException("Bộ lọc không hợp lệ.");
                }
            }
        }
        return true;
    }
}
