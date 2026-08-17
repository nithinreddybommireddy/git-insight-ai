package com.gitinsight.authservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {

    /**
     * Tokens are delivered exclusively via HttpOnly session cookies set by
     * {@code AuthCookieService}. They must never appear in the JSON body —
     * any JavaScript running in the page (or a browser extension, analytics
     * script, etc.) could otherwise read them. The getters stay for the
     * cookie builder; Jackson simply never serializes the fields.
     */
    @JsonIgnore
    private String token;

    @JsonIgnore
    private String refreshToken;

    private UserResponse user;
}
