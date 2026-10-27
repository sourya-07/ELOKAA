package in.eko.eloka.partner.shared.logging;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * First filter of every request: assigns a request id (or accepts a sane incoming
 * {@code X-Request-Id} from the client's proxy), echoes it, puts it in the logging context,
 * and writes one access-log line when the request ends. The query string is never logged.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Request-Id";

	private static final Logger access = LoggerFactory.getLogger("eloka.access");
	private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String incoming = request.getHeader(HEADER);
		String requestId = incoming != null && SAFE_ID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
		long started = System.nanoTime();
		MDC.put("request_id", requestId);
		response.setHeader(HEADER, requestId);
		try {
			chain.doFilter(request, response);
		}
		finally {
			long millis = (System.nanoTime() - started) / 1_000_000;
			if (!request.getRequestURI().startsWith("/actuator")) {
				access.info("{} {} http={} ms={}", request.getMethod(), request.getRequestURI(), response.getStatus(),
						millis);
			}
			MDC.clear();
		}
	}
}
