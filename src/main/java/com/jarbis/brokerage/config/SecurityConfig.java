package com.jarbis.brokerage.config;

import com.jarbis.brokerage.filter.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration with JWT authentication.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
		http.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authorize -> authorize
				// Public endpoints
				.requestMatchers("/actuator/health").permitAll()
				.requestMatchers(HttpMethod.POST, "/auth/register").permitAll()
				.requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
				// Protected endpoints
				.requestMatchers("/users/**").authenticated()
				.requestMatchers("/orders/**").authenticated()
				.requestMatchers("/accounts/**").authenticated()
				.requestMatchers("/holdings/**").authenticated()
				.requestMatchers("/transactions/**").authenticated()
				// Require auth for all other endpoints
				.anyRequest().authenticated())
			.httpBasic(httpBasic -> httpBasic.disable())
			.formLogin(form -> form.disable())
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}
