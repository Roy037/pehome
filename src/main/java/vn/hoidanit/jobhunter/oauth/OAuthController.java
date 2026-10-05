package vn.hoidanit.jobhunter.oauth;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.response.ResLoginDTO;
import vn.hoidanit.jobhunter.oauth.OAuthProvider.Profile;
import vn.hoidanit.jobhunter.service.UserService;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;

/**
 * Social sign-in. The browser is sent to the provider ({@code /authorize}), comes back to {@code /callback}, and is then
 * sent on to the site with a refresh-token cookie; the site turns that into an access token exactly like a page reload does.
 * Every failure ends on the log-in page as {@code /login?oauth_error=<code>}.
 */
@RestController
@RequestMapping("/api/v1/auth/oauth")
public class OAuthController {

    private static final Logger log = LoggerFactory.getLogger(OAuthController.class);
    private static final String STATE_COOKIE = "oauth_state";
    private static final String STATE_COOKIE_PATH = "/api/v1/auth/oauth";

    private final OAuthService oauth;
    private final OAuthState oauthState;
    private final OAuthAccountService accounts;
    private final UserService userService;
    private final SecurityUtil securityUtil;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${hoidanit.jwt.refresh-token-validity-in-seconds}")
    private long refreshTokenExpiration;

    public OAuthController(OAuthService oauth, OAuthState oauthState, OAuthAccountService accounts, UserService userService,
            SecurityUtil securityUtil) {
        this.oauth = oauth;
        this.oauthState = oauthState;
        this.accounts = accounts;
        this.userService = userService;
        this.securityUtil = securityUtil;
    }

    public record ProviderDTO(String code, boolean enabled) {
    }

    /** Which buttons the log-in forms may enable. */
    @GetMapping("/providers")
    @ApiMessage("Social sign-in providers")
    public ResponseEntity<List<ProviderDTO>> providers() {
        return ResponseEntity.ok(Arrays.stream(OAuthProvider.values()).map(p -> new ProviderDTO(p.code().toUpperCase(), this.oauth.enabled(p))).toList());
    }

    @GetMapping("/{provider}/authorize")
    public void authorize(@PathVariable("provider") String code, @RequestParam(value = "next", required = false) String next,
            HttpServletResponse response) throws IOException {
        OAuthProvider provider = OAuthProvider.fromCode(code).orElse(null);
        if (provider == null || !this.oauth.enabled(provider)) {
            fail(response, OAuthException.DISABLED);
            return;
        }
        String nonce = this.oauthState.newNonce();
        response.addHeader(HttpHeaders.SET_COOKIE, stateCookie(nonce, 600).toString());
        response.sendRedirect(this.oauth.authorizationUri(provider, this.oauthState.create(nonce, next)).toString());
    }

    @GetMapping("/{provider}/callback")
    public void callback(@PathVariable("provider") String code, @RequestParam(value = "code", required = false) String authCode,
            @RequestParam(value = "state", required = false) String state, @RequestParam(value = "error", required = false) String error,
            @CookieValue(name = STATE_COOKIE, required = false) String cookieNonce, HttpServletResponse response) throws IOException {
        response.addHeader(HttpHeaders.SET_COOKIE, stateCookie("", 0).toString()); // single use
        OAuthProvider provider = OAuthProvider.fromCode(code).orElse(null);
        if (provider == null || !this.oauth.enabled(provider)) {
            fail(response, OAuthException.DISABLED);
            return;
        }
        if (error != null) {
            fail(response, "access_denied".equals(error) ? OAuthException.DENIED : OAuthException.FAILED);
            return;
        }
        String next = this.oauthState.verify(state, cookieNonce).orElse(null);
        if (next == null || authCode == null || authCode.isBlank()) {
            log.warn("OAuth callback for {} rejected: bad state or no code", provider);
            fail(response, OAuthException.FAILED);
            return;
        }
        try {
            Profile profile = this.oauth.exchange(provider, authCode);
            User user = this.accounts.signIn(profile);

            ResLoginDTO session = new ResLoginDTO();
            session.setUser(ResLoginDTO.UserLogin.from(user));
            String refreshToken = this.securityUtil.createRefreshToken(user.getEmail(), session);
            this.userService.updateUserToken(refreshToken, user.getEmail());
            response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("refresh_token", refreshToken)
                    .httpOnly(true).secure(true).path("/").maxAge(this.refreshTokenExpiration).build().toString());
            response.sendRedirect(this.frontendUrl + "/dang-nhap-xong?next=" + URLEncoder.encode(next, StandardCharsets.UTF_8));
        } catch (OAuthException e) {
            log.warn("OAuth sign-in with {} refused ({}): {}", provider, e.code(), e.getMessage());
            fail(response, e.code());
        } catch (RuntimeException e) {
            log.error("OAuth sign-in with {} failed", provider, e);
            fail(response, OAuthException.FAILED);
        }
    }

    private void fail(HttpServletResponse response, String code) throws IOException {
        response.sendRedirect(this.frontendUrl + "/login?oauth_error=" + code);
    }

    private ResponseCookie stateCookie(String nonce, long maxAgeSeconds) {
        // Lax: the cookie must come back on the top-level redirect from the provider
        return ResponseCookie.from(STATE_COOKIE, nonce).httpOnly(true).secure(this.frontendUrl.startsWith("https"))
                .sameSite("Lax").path(STATE_COOKIE_PATH).maxAge(maxAgeSeconds).build();
    }
}
