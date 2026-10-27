package in.eko.eloka.partner.shared.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import in.eko.eloka.partner.shared.config.ElokaProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * HTTP security. Authentication is an opaque bearer token resolved by {@link CallerResolver};
 * the browser never holds it (the Next.js proxy does, ADR-0003). Default deny: a route is
 * reachable only when listed here. Errors are problem+json.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET) // not in the one-shot migration job
public class SecurityConfig {

	/** Used until the identity module provides real sessions: no token resolves to a caller. */
	private static final CallerResolver NO_SESSIONS = token -> Optional.empty();

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectProvider<CallerResolver> callerResolver)
			throws Exception {
		return http
				.cors(Customizer.withDefaults())
				.csrf(csrf -> csrf.disable()) // bearer tokens only, no cookies on this API → no CSRF exposure
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.addFilterBefore(new BearerTokenFilter(callerResolver.getIfAvailable(() -> NO_SESSIONS)),
						AnonymousAuthenticationFilter.class)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
						// Applicants: sign-in is public; everything else needs an applicant session
						.requestMatchers(HttpMethod.POST, "/v1/partner/otp", "/v1/partner/otp/verify",
								"/v1/partner/session/refresh").permitAll()
						.requestMatchers("/v1/partner/**").hasAuthority("KIND_" + SessionKind.PARTNER_APPLICANT.name())
						// Staff: sign-in is public; everything else needs a staff session (roles checked per action)
						.requestMatchers(HttpMethod.POST, "/v1/admin/auth/google", "/v1/admin/auth/otp",
								"/v1/admin/auth/otp/verify", "/v1/admin/auth/refresh").permitAll()
						.requestMatchers("/v1/admin/**").hasAuthority("KIND_" + SessionKind.STAFF.name())
						.anyRequest().denyAll())
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint((request, response, e) -> writeProblem(response,
								HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED",
								"Your session has expired. Please sign in again."))
						.accessDeniedHandler((request, response, e) -> writeProblem(response,
								HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "You are not allowed to do this.")))
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.logout(logout -> logout.disable())
				.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(ElokaProperties properties) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOriginPatterns(properties.corsAllowedOriginPatterns());
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type", "If-Match", "Idempotency-Key", "X-Request-Id"));
		config.setExposedHeaders(List.of("X-Request-Id", "ETag"));
		config.setAllowCredentials(false);
		config.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	private static void writeProblem(HttpServletResponse response, int status, String code, String detail)
			throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.getWriter().write("{\"status\":" + status + ",\"code\":\"" + code + "\",\"detail\":\"" + detail + "\"}");
	}
}
