package vn.hoidanit.jobhunter.oauth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.hoidanit.jobhunter.oauth.OAuthProvider.Profile;

/**
 * The Authorization Code flow, run by the server so the client secret never reaches the browser: build the provider's
 * sign-in address, then swap the returned code for a token and read who signed in. Nothing sensitive is ever logged.
 */
@Service
public class OAuthService {

    private static final Logger log = LoggerFactory.getLogger(OAuthService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final OAuthProperties properties;
    private final String backendUrl;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public OAuthService(OAuthProperties properties, @Value("${app.backend-url:http://localhost:8080}") String backendUrl) {
        this.properties = properties;
        this.backendUrl = backendUrl.endsWith("/") ? backendUrl.substring(0, backendUrl.length() - 1) : backendUrl;
    }

    public boolean enabled(OAuthProvider provider) {
        return this.properties.enabled(provider);
    }

    /** The address to register as "redirect URI" in the provider's console. */
    public String redirectUri(OAuthProvider provider) {
        return this.backendUrl + "/api/v1/auth/oauth/" + provider.code() + "/callback";
    }

    public URI authorizationUri(OAuthProvider provider, String state) {
        OAuthProperties.Settings settings = this.properties.of(provider);
        Map<String, String> query = new LinkedHashMap<>();
        query.put("client_id", settings.getClientId());
        query.put("redirect_uri", redirectUri(provider));
        query.put("response_type", "code");
        query.put("scope", provider.scope());
        query.put("state", state);
        if (provider == OAuthProvider.GOOGLE) {
            query.put("prompt", "select_account"); // always let the user pick the account
        }
        return URI.create(settings.getAuthorizeUrl() + (settings.getAuthorizeUrl().contains("?") ? "&" : "?") + form(query));
    }

    /** Swaps the one-time code for a token and reads the profile. */
    public Profile exchange(OAuthProvider provider, String code) {
        OAuthProperties.Settings settings = this.properties.of(provider);
        try {
            String token = token(provider, settings, code);
            JsonNode json = userinfo(provider, settings, token);
            Profile profile = provider.profile(json);
            if (profile.subject() == null) {
                throw new OAuthException(OAuthException.FAILED, provider + " returned a profile without an id");
            }
            return profile;
        } catch (IOException e) {
            throw new OAuthException(OAuthException.FAILED, provider + " request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OAuthException(OAuthException.FAILED, provider + " request interrupted", e);
        }
    }

    private String token(OAuthProvider provider, OAuthProperties.Settings settings, String code) throws IOException, InterruptedException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("code", code);
        form.put("client_id", settings.getClientId());
        form.put("client_secret", settings.getClientSecret());
        form.put("redirect_uri", redirectUri(provider));
        HttpRequest.Builder request;
        if (provider.tokenViaGet()) {
            request = HttpRequest.newBuilder(URI.create(settings.getTokenUrl() + "?" + form(form))).GET();
        } else {
            form.put("grant_type", "authorization_code");
            request = HttpRequest.newBuilder(URI.create(settings.getTokenUrl()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form(form)));
        }
        JsonNode json = send(provider, request.header("Accept", "application/json").timeout(TIMEOUT).build());
        JsonNode token = json.get("access_token");
        if (token == null || token.asText().isBlank()) {
            throw new OAuthException(OAuthException.FAILED, provider + " token answer had no access_token");
        }
        return token.asText();
    }

    private JsonNode userinfo(OAuthProvider provider, OAuthProperties.Settings settings, String token) throws IOException, InterruptedException {
        HttpRequest.Builder request;
        if (provider == OAuthProvider.FACEBOOK) {
            String url = settings.getUserinfoUrl() + (settings.getUserinfoUrl().contains("?") ? "&" : "?")
                    + form(Map.of("fields", "id,name,email", "access_token", token));
            request = HttpRequest.newBuilder(URI.create(url)).GET();
        } else {
            request = HttpRequest.newBuilder(URI.create(settings.getUserinfoUrl())).GET().header("Authorization", "Bearer " + token);
        }
        return send(provider, request.header("Accept", "application/json").timeout(TIMEOUT).build());
    }

    private JsonNode send(OAuthProvider provider, HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = this.http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            log.warn("{} answered HTTP {} to {}", provider, response.statusCode(), request.uri().getPath());
            throw new OAuthException(OAuthException.FAILED, provider + " answered HTTP " + response.statusCode());
        }
        return this.mapper.readTree(response.body());
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
