package com.jarbis.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.mockito.Mockito.*;
import java.util.Map;

@WebMvcTest({AnalyticsController.class, SessionController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties={"analytics.allowed-origins=http://localhost:4200", "analytics.username=analyst", "analytics.password-hash=$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"})
class SecurityTest {
    @Autowired MockMvc mvc;
    @MockBean AnalyticsRepository repository;
    @Autowired org.springframework.security.core.userdetails.UserDetailsService users;
    @Test void anonymousApiGets401AndClientRoleIsForbidden() throws Exception {
        mvc.perform(get("/admin/analytics/options")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/analytics/options").with(user("client").roles("CLIENT"))).andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }
    @Test void analystCanReadAndResponsesHaveStrictCsp() throws Exception {
        when(repository.options()).thenReturn(Map.of("instruments",java.util.List.of()));
        mvc.perform(get("/admin/analytics/options").with(user("analyst").roles("ANALYST")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.instruments").isArray())
            .andExpect(header().string("Content-Security-Policy",org.hamcrest.Matchers.containsString("default-src 'none'")));
    }
    @Test void invalidRangeIsRejectedBeforeQuerying() throws Exception {
        mvc.perform(get("/admin/analytics/dashboard?from=2026-03-30&to=2026-03-01").with(user("analyst").roles("ANALYST")))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }
    @Test void sessionApiHasNoHtmlRedirectAndFailedLoginReturns401() throws Exception {
        mvc.perform(get("/session/me")).andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Location"));
        mvc.perform(get("/session/me").with(user("analyst").roles("ANALYST")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("analyst"));
        mvc.perform(post("/login").with(csrf()).param("username","analyst").param("password","wrong"))
            .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Location"));
        mvc.perform(post("/logout").with(csrf()).with(user("analyst").roles("ANALYST")))
            .andExpect(status().isNoContent()).andExpect(header().doesNotExist("Location"));
    }
    @Test void loginReturns204AndCreatesAnAuthenticatedSession() throws Exception {
        var manager = (org.springframework.security.provisioning.InMemoryUserDetailsManager) users;
        manager.updateUser(org.springframework.security.core.userdetails.User.withUsername("analyst")
            .password("{bcrypt}" + new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("test-only"))
            .roles("ANALYST").build());
        var result = mvc.perform(post("/login").with(csrf()).param("username","analyst").param("password","test-only"))
            .andExpect(status().isNoContent()).andExpect(header().doesNotExist("Location")).andReturn();
        mvc.perform(get("/session/me").session((org.springframework.mock.web.MockHttpSession) result.getRequest().getSession(false)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("analyst"));
        mvc.perform(get("/login")).andExpect(status().is4xxClientError());
    }
    @Test void allowsOnlyConfiguredFrontendOrigin() throws Exception {
        mvc.perform(options("/admin/analytics/dashboard").header("Origin","http://localhost:4200")
            .header("Access-Control-Request-Method","GET"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:4200"))
            .andExpect(header().string("Access-Control-Allow-Credentials","true"));
        mvc.perform(options("/admin/analytics/dashboard").header("Origin","https://untrusted.example")
            .header("Access-Control-Request-Method","GET")).andExpect(status().isForbidden());
    }
    @Test void loginAndLogoutRequireCsrf() throws Exception {
        mvc.perform(post("/login").param("username","analyst").param("password","bad")).andExpect(status().isForbidden());
        mvc.perform(post("/logout").with(user("analyst").roles("ANALYST"))).andExpect(status().isForbidden());
        mvc.perform(get("/session/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
    }
}
