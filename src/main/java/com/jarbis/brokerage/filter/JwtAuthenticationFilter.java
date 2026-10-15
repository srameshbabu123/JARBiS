package com.jarbis.brokerage.filter;

import com.jarbis.brokerage.service.JwtService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * JWT Authentication Filter
 * Validates JWT tokens locally and populates the security context.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;

	public JwtAuthenticationFilter(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String authHeader = request.getHeader("Authorization");
		if (authHeader != null && authHeader.startsWith("Bearer ")) {
			String token = authHeader.substring(7);
			try {
				JwtService.JwtPrincipal principal = jwtService.parseToken(token);
				UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
						principal.userId(), null, null);
				request.setAttribute("userId", principal.userId());
				request.setAttribute("email", principal.email());
				request.setAttribute("fullName", principal.fullName());
				SecurityContextHolder.getContext().setAuthentication(authToken);
			} catch (Exception e) {
				logger.warn("Invalid JWT: " + e.getMessage());
			}
		}

		filterChain.doFilter(request, response);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
		// Skip filter for public endpoints
		String path = request.getRequestURI();
		return path.startsWith("/actuator") || path.equals("/auth/register") || path.equals("/auth/login")
				|| path.contains("health");
	}
}
