package vn.hoidanit.jobhunter.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import vn.hoidanit.jobhunter.oauth.OAuthProvider.Profile;

// Runs the real code against a tiny fake provider on localhost, so the request shapes and the answers are checked without real keys.
class OAuthServiceTests {
    HttpServer server;
    final Map<String, String> answers = new ConcurrentHashMap<>();
    final Map<String, Integer> statuses = new ConcurrentHashMap<>();
    final List<String> seen = new ArrayList<>();
    OAuthService service;
    String base;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String path = exchange.getRequestURI().getPath();
            seen.add(exchange.getRequestMethod() + " " + path + " q=" + exchange.getRequestURI().getRawQuery() + " body=" + body
                    + " auth=" + exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] out = answers.getOrDefault(path, "{}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statuses.getOrDefault(path, 200), out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        OAuthProperties properties = new OAuthProperties();
        for (OAuthProvider provider : OAuthProvider.values()) {
            OAuthProperties.Settings settings = new OAuthProperties.Settings();
            settings.setClientId("cid-" + provider.code());
            settings.setClientSecret("secret-" + provider.code());
            settings.setAuthorizeUrl(base + "/" + provider.code() + "/authorize");
            settings.setTokenUrl(base + "/" + provider.code() + "/token");
            settings.setUserinfoUrl(base + "/" + provider.code() + "/userinfo");
            properties.getProviders().put(provider.code(), settings);
        }
        service = new OAuthService(properties, "https://api.itjobs.test/");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    @Test
    void aProviderIsOnOnlyWhenKeysAreSet() {
        assertTrue(service.enabled(OAuthProvider.GOOGLE));
        assertFalse(new OAuthService(new OAuthProperties(), "http://x").enabled(OAuthProvider.GOOGLE));
        OAuthProperties half = new OAuthProperties();
        OAuthProperties.Settings settings = new OAuthProperties.Settings();
        settings.setClientId("only-an-id");
        half.getProviders().put("google", settings);
        assertFalse(new OAuthService(half, "http://x").enabled(OAuthProvider.GOOGLE));
    }

    @Test
    void theSignInAddressCarriesTheClientTheCallbackAndTheState() {
        URI uri = service.authorizationUri(OAuthProvider.GOOGLE, "the.state");
        assertEquals("/GOOGLE".toLowerCase() + "/authorize", uri.getPath());
        String query = decode(uri.getRawQuery());
        assertTrue(query.contains("client_id=cid-google") && query.contains("response_type=code") && query.contains("state=the.state"));
        assertTrue(query.contains("redirect_uri=https://api.itjobs.test/api/v1/auth/oauth/google/callback"), "trailing slash of BACKEND_URL is dropped");
        assertTrue(query.contains("scope=openid email profile") && query.contains("prompt=select_account"));
        assertFalse(decode(service.authorizationUri(OAuthProvider.FACEBOOK, "s").getRawQuery()).contains("prompt="));
        assertTrue(decode(service.authorizationUri(OAuthProvider.LINKEDIN, "s").getRawQuery()).contains("scope=openid profile email"));
    }

    @Test
    void google() {
        answers.put("/google/token", "{\"access_token\":\"tok-g\",\"token_type\":\"Bearer\"}");
        answers.put("/google/userinfo", "{\"sub\":\"1234\",\"email\":\"Ann@Example.com\",\"email_verified\":true,\"name\":\"Ann Nguyen\"}");
        Profile profile = service.exchange(OAuthProvider.GOOGLE, "the-code");
        assertEquals(new Profile(OAuthProvider.GOOGLE, "1234", "Ann@Example.com", true, "Ann Nguyen"), profile);
        String token = seen.get(0);
        assertTrue(token.startsWith("POST /google/token"));
        assertTrue(decode(token).contains("code=the-code") && token.contains("client_secret=secret-google") && token.contains("grant_type=authorization_code"));
        assertTrue(seen.get(1).contains("auth=Bearer tok-g"), "the profile is read with the access token");
    }

    @Test
    void linkedin() {
        answers.put("/linkedin/token", "{\"access_token\":\"tok-l\"}");
        answers.put("/linkedin/userinfo", "{\"sub\":\"abc\",\"email\":\"lee@example.com\",\"email_verified\":\"true\",\"name\":\"Lee\"}");
        Profile profile = service.exchange(OAuthProvider.LINKEDIN, "c");
        assertEquals(new Profile(OAuthProvider.LINKEDIN, "abc", "lee@example.com", true, "Lee"), profile);
    }

    @Test
    void facebookUsesGetsAndHasNoVerifiedFlag() {
        answers.put("/facebook/token", "{\"access_token\":\"tok-f\"}");
        answers.put("/facebook/userinfo", "{\"id\":\"99\",\"name\":\"Fay\",\"email\":\"fay@example.com\"}");
        assertEquals(new Profile(OAuthProvider.FACEBOOK, "99", "fay@example.com", true, "Fay"), service.exchange(OAuthProvider.FACEBOOK, "c"));
        assertTrue(seen.get(0).startsWith("GET /facebook/token") && seen.get(0).contains("code=c"));
        assertTrue(seen.get(1).contains("fields=id%2Cname%2Cemail") && seen.get(1).contains("access_token=tok-f"));

        answers.put("/facebook/userinfo", "{\"id\":\"100\",\"name\":\"No Mail\"}");
        Profile noMail = service.exchange(OAuthProvider.FACEBOOK, "c");
        assertNull(noMail.email());
        assertFalse(noMail.emailVerified());
    }

    @Test
    void aBadAnswerFromTheProviderIsAFailureNotACrash() {
        statuses.put("/google/token", 400);
        assertEquals(OAuthException.FAILED, assertThrows(OAuthException.class, () -> service.exchange(OAuthProvider.GOOGLE, "c")).code());

        statuses.clear();
        answers.put("/google/token", "{\"error\":\"invalid_grant\"}");
        assertEquals(OAuthException.FAILED, assertThrows(OAuthException.class, () -> service.exchange(OAuthProvider.GOOGLE, "c")).code());

        answers.put("/google/token", "{\"access_token\":\"t\"}");
        answers.put("/google/userinfo", "{\"email\":\"a@b.c\"}");
        assertEquals(OAuthException.FAILED, assertThrows(OAuthException.class, () -> service.exchange(OAuthProvider.GOOGLE, "c")).code(), "no subject");

        answers.put("/google/userinfo", "not json");
        assertEquals(OAuthException.FAILED, assertThrows(OAuthException.class, () -> service.exchange(OAuthProvider.GOOGLE, "c")).code());
    }
}
