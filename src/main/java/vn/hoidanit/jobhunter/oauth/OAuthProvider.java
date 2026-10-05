package vn.hoidanit.jobhunter.oauth;

import java.util.Locale;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

/** The three sign-in providers and the few places where their answers differ. Endpoints and secrets live in {@link OAuthProperties}. */
public enum OAuthProvider {
    GOOGLE("openid email profile", false) {
        @Override
        Profile profile(JsonNode json) {
            return new Profile(this, text(json, "sub"), text(json, "email"), flag(json, "email_verified"), text(json, "name"));
        }
    },
    LINKEDIN("openid profile email", false) {
        @Override
        Profile profile(JsonNode json) {
            return new Profile(this, text(json, "sub"), text(json, "email"), flag(json, "email_verified"), text(json, "name"));
        }
    },
    FACEBOOK("email,public_profile", true) {
        @Override
        Profile profile(JsonNode json) {
            String email = text(json, "email");
            // Facebook only hands out an address it has on file for the account and has no "verified" flag: present = accepted.
            return new Profile(this, text(json, "id"), email, email != null, text(json, "name"));
        }
    };

    /** Who the user is, as the provider tells us. */
    public record Profile(OAuthProvider provider, String subject, String email, boolean emailVerified, String name) {
    }

    private final String scope;
    private final boolean tokenViaGet;

    OAuthProvider(String scope, boolean tokenViaGet) {
        this.scope = scope;
        this.tokenViaGet = tokenViaGet;
    }

    public String scope() {
        return this.scope;
    }

    /** Facebook's token endpoint is a GET; the others take a form POST. */
    public boolean tokenViaGet() {
        return this.tokenViaGet;
    }

    /** The {@code /auth/oauth/{code}/...} path segment and the setting key. */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    abstract Profile profile(JsonNode json);

    public static Optional<OAuthProvider> fromCode(String code) {
        for (OAuthProvider provider : values()) {
            if (provider.code().equalsIgnoreCase(code)) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }

    static String text(JsonNode json, String field) {
        JsonNode node = json.get(field);
        return node == null || node.isNull() || node.asText().isBlank() ? null : node.asText().trim();
    }

    // booleans come as true/false or as "true"
    static boolean flag(JsonNode json, String field) {
        JsonNode node = json.get(field);
        return node != null && ("true".equalsIgnoreCase(node.asText()));
    }
}
