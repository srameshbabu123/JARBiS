package com.jarbis.analytics;

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
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

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
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a
                .requestMatchers("/login.html", "/login", "/login.js", "/styles.css", "/session/csrf", "/actuator/health").permitAll()
                .anyRequest().hasRole("ANALYST"))
            .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                new AntPathRequestMatcher("/admin/**")))
            .formLogin(f -> f.loginPage("/login.html").loginProcessingUrl("/login")
                .defaultSuccessUrl("/", true).failureUrl("/login.html?error"))
            .logout(l -> l.logoutSuccessUrl("/login.html"))
            .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives(
                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")))
            .build();
    }
}
