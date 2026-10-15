package com.jarbis.analytics;

import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController {
    @GetMapping("/session/me")
    Map<String, Object> me(org.springframework.security.core.Authentication authentication) {
        return Map.of("username", authentication.getName(), "roles", authentication.getAuthorities().stream()
            .map(org.springframework.security.core.GrantedAuthority::getAuthority).toList());
    }

    @GetMapping("/session/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("parameterName", token.getParameterName(), "headerName", token.getHeaderName(), "token", token.getToken());
    }
}
