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
@TestPropertySource(properties={"analytics.username=analyst", "analytics.password-hash=$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"})
class SecurityTest {
    @Autowired MockMvc mvc;
    @MockBean AnalyticsRepository repository;
    @Test void anonymousApiGets401AndClientRoleIsForbidden() throws Exception {
        mvc.perform(get("/admin/analytics/options")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/analytics/options").with(user("client").roles("CLIENT"))).andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }
    @Test void analystCanReadAndResponsesHaveStrictCsp() throws Exception {
        when(repository.options()).thenReturn(Map.of("instruments",java.util.List.of()));
        mvc.perform(get("/admin/analytics/options").with(user("analyst").roles("ANALYST")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.instruments").isArray())
            .andExpect(header().string("Content-Security-Policy",org.hamcrest.Matchers.containsString("script-src 'self'")));
    }
    @Test void invalidRangeIsRejectedBeforeQuerying() throws Exception {
        mvc.perform(get("/admin/analytics/dashboard?from=2026-03-30&to=2026-03-01").with(user("analyst").roles("ANALYST")))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }
    @Test void loginAndLogoutRequireCsrf() throws Exception {
        mvc.perform(post("/login").param("username","analyst").param("password","bad")).andExpect(status().isForbidden());
        mvc.perform(post("/logout").with(user("analyst").roles("ANALYST"))).andExpect(status().isForbidden());
        mvc.perform(get("/session/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
    }
}
