package com.jarbis.analytics;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {
    @Bean
    UserDetailsService internalUsers(@Value("${analytics.username}") String username,
                                    @Value("${analytics.password-hash}") String hash) {
        if (!hash.matches("\\$2[aby]\\$\\d{2}\\$.{53}")) {
            throw new IllegalArgumentException("ANALYTICS_ADMIN_PASSWORD_HASH must be a BCrypt hash");
        }
        return new InMemoryUserDetailsManager(User.withUsername(username).password("{bcrypt}" + hash)
            .roles("ANALYST").build());
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${analytics.allowed-origins:}") String origins) {
        CorsConfiguration cors = new CorsConfiguration();
        List<String> allowed = Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (allowed.stream().anyMatch(s -> s.contains("*") || !(s.startsWith("https://") || s.startsWith("http://")))) {
            throw new IllegalArgumentException("Configure explicit http(s) frontend origins; wildcards are not allowed");
        }
        cors.setAllowedOrigins(allowed);
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN", "X-Requested-With"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.cors(c -> {})
            .authorizeHttpRequests(a -> a
                .requestMatchers("/login", "/session/csrf", "/actuator/health").permitAll()
                .anyRequest().hasRole("ANALYST"))
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .requestCache(c -> c.disable())
            .formLogin(f -> f.loginPage("/session/login").loginProcessingUrl("/login")
                .successHandler((request, response, authentication) -> response.setStatus(204))
                .failureHandler((request, response, exception) -> response.sendError(401)))
            .logout(l -> l.logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
            .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'; base-uri 'none'")))
            .build();
    }
}
