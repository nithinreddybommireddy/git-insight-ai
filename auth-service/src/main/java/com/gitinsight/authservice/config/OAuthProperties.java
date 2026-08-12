package com.gitinsight.authservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * GitHub OAuth configuration. All values come from the environment so no
 * credentials (or mock fallbacks) live in source code:
 *
 * <pre>
 *   GITHUB_CLIENT_ID          GitHub OAuth App client id
 *   GITHUB_CLIENT_SECRET      GitHub OAuth App client secret
 *   GITHUB_OAUTH_REDIRECT_URI Public callback URL registered in the OAuth app
 *   OAUTH_FRONTEND_REDIRECT_URI Where the browser lands after login
 * </pre>
 *
 * The client id/secret are optional: when unset, the OAuth entry points return
 * a clear error instead of attempting a flow that cannot succeed.
 */
@Component
public class OAuthProperties {

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String frontendRedirectUri;

    public OAuthProperties(
            @Value("${app.oauth.github.client-id:}") String clientId,
            @Value("${app.oauth.github.client-secret:}") String clientSecret,
            @Value("${app.oauth.github.redirect-uri:}") String redirectUri,
            @Value("${app.oauth.github.frontend-redirect-uri:}") String frontendRedirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.frontendRedirectUri = frontendRedirectUri;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public String getFrontendRedirectUri() {
        return frontendRedirectUri;
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
