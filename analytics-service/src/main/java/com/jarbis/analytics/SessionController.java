package com.jarbis.analytics;

import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController {
    @GetMapping("/session/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("parameterName", token.getParameterName(), "headerName", token.getHeaderName(), "token", token.getToken());
    }
}
