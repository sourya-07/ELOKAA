package in.eko.eloka.partner.onboarding.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import in.eko.eloka.partner.shared.error.ApiProblem;
import in.eko.eloka.partner.shared.error.ValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code Idempotency-Key} for side-effect POSTs (docs/API.md). The first request
 * with a key runs; repeats within 24 hours get the stored response. The same key with a
 * different request is {@code 422 IDEMPOTENCY_KEY_REUSED}; a repeat while the first is still
 * running is {@code 409 IDEMPOTENCY_IN_PROGRESS}. Failed requests are not stored, so they can be
 * retried with the same key.
 */
@Component
class Idempotency {

	static final String HEADER = "Idempotency-Key";
	private static final Duration TTL = Duration.ofHours(24);

	private final IdempotencyRecordRepository records;
	private final TransactionTemplate newTransaction;
	private final JsonMapper json;
	private final Clock clock;

	Idempotency(IdempotencyRecordRepository records, PlatformTransactionManager transactions, JsonMapper json,
			Clock clock) {
		this.records = records;
		this.newTransaction = new TransactionTemplate(transactions);
		this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.json = json;
		this.clock = clock;
	}

	/**
	 * Runs {@code action} once per {@code (scope, key)}.
	 *
	 * @param scope who the key belongs to (applicant id, or a per-mobile id before login)
	 * @param key the client's {@code Idempotency-Key} header
	 * @param fingerprint everything that identifies the request (method, path, body)
	 */
	ResponseEntity<?> run(UUID scope, String key, String fingerprint, Supplier<ResponseEntity<?>> action) {
		String idemKey = validKey(key);
		byte[] requestHash = sha256(fingerprint);
		Optional<ResponseEntity<?>> replay = newTransaction.execute(status -> claim(scope, idemKey, requestHash));
		if (replay.isPresent()) {
			return replay.get();
		}
		ResponseEntity<?> response;
		try {
			response = action.get();
		}
		catch (RuntimeException ex) {
			newTransaction.executeWithoutResult(status -> records.findByScopeIdAndIdemKey(scope, idemKey)
				.filter(record -> !record.isComplete())
				.ifPresent(records::delete));
			throw ex;
		}
		String body = response.getBody() == null ? null : json.writeValueAsString(response.getBody());
		String etag = response.getHeaders().getETag();
		newTransaction.executeWithoutResult(status -> records.findByScopeIdAndIdemKey(scope, idemKey)
			.ifPresent(record -> record.complete(response.getStatusCode().value(), etag, body)));
		return response;
	}

	/** Records the key as in flight, or returns the stored response. */
	private Optional<ResponseEntity<?>> claim(UUID scope, String idemKey, byte[] requestHash) {
		Instant now = Instant.now(clock);
		Optional<IdempotencyRecord> existing = records.findByScopeIdAndIdemKey(scope, idemKey);
		if (existing.isPresent() && existing.get().isExpired(now)) {
			records.delete(existing.get());
			records.flush();
			existing = Optional.empty();
		}
		if (existing.isEmpty()) {
			try {
				records.saveAndFlush(new IdempotencyRecord(scope, idemKey, requestHash, now.plus(TTL)));
				return Optional.empty();
			}
			catch (DataIntegrityViolationException race) {
				throw new ApiProblem(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_PROGRESS",
						"The same request is already being processed. Try again in a moment.");
			}
		}
		IdempotencyRecord record = existing.get();
		if (!MessageDigest.isEqual(record.getRequestHash(), requestHash)) {
			throw new ApiProblem(HttpStatus.UNPROCESSABLE_CONTENT, "IDEMPOTENCY_KEY_REUSED",
					"This Idempotency-Key was already used for a different request");
		}
		if (!record.isComplete()) {
			throw new ApiProblem(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_PROGRESS",
					"The same request is already being processed. Try again in a moment.");
		}
		ResponseEntity.BodyBuilder replay = ResponseEntity.status(record.getResponseStatus())
			.header("Idempotent-Replayed", "true");
		if (record.getResponseEtag() != null) {
			replay.header(HttpHeaders.ETAG, record.getResponseEtag());
		}
		if (record.getResponseBody() == null) {
			return Optional.of(replay.build());
		}
		return Optional.of(replay.contentType(MediaType.APPLICATION_JSON).body(record.getResponseBody()));
	}

	@Scheduled(initialDelayString = "PT15M", fixedDelayString = "PT6H")
	void purgeExpired() {
		newTransaction.executeWithoutResult(status -> records.deleteExpired(Instant.now(clock)));
	}

	private static String validKey(String key) {
		if (key == null || key.isBlank()) {
			throw new ApiProblem(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
					"Send an Idempotency-Key header (a new UUID per action)");
		}
		String value = key.trim();
		if (value.length() > 80 || !value.matches("[A-Za-z0-9-]+")) {
			throw ValidationException.field(HEADER, "Use a UUID");
		}
		return value;
	}

	private static byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
