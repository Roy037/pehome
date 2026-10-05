package vn.hoidanit.jobhunter.oauth;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/** {@code app.oauth.providers.<google|facebook|linkedin>.*}: a provider is on once it has a client id and secret. */
@Component
@ConfigurationProperties(prefix = "app.oauth")
@Getter
@Setter
public class OAuthProperties {

    private Map<String, Settings> providers = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class Settings {
        private String clientId = "";
        private String clientSecret = "";
        private String authorizeUrl = "";
        private String tokenUrl = "";
        private String userinfoUrl = "";

        public boolean configured() {
            return !clientId.isBlank() && !clientSecret.isBlank() && !authorizeUrl.isBlank() && !tokenUrl.isBlank()
                    && !userinfoUrl.isBlank();
        }
    }

    public Settings of(OAuthProvider provider) {
        return this.providers.getOrDefault(provider.code(), new Settings());
    }

    public boolean enabled(OAuthProvider provider) {
        return of(provider).configured();
    }
}
