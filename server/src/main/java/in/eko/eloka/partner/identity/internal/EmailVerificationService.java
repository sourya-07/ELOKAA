package in.eko.eloka.partner.identity.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import in.eko.eloka.partner.identity.EmailVerification;
import in.eko.eloka.partner.identity.IdentityProperties;
import in.eko.eloka.partner.integration.EmailSender;
import in.eko.eloka.partner.shared.crypto.BlindIndex;
import in.eko.eloka.partner.shared.crypto.FieldCipher;
import in.eko.eloka.partner.shared.crypto.Masking;
import in.eko.eloka.partner.shared.error.ApiProblem;
import in.eko.eloka.partner.shared.error.RateLimitedException;
import in.eko.eloka.partner.shared.error.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Email codes, with the mobile-OTP limits applied per address, per client IP and per application.
 * Sending follows {@link OtpService}: lock the guard rows, check the limits and reserve the
 * challenge (one short transaction), send outside any transaction, and drop the reservation if
 * the mail server refuses, so a failed send does not count.
 */
@Service
class EmailVerificationService implements EmailVerification {

	private static final Pattern EMAIL = Pattern.compile("[^\\s@]{1,64}@[^\\s@]+\\.[^\\s@.]{2,}");
	private static final String SUBJECT = "Your ELOKA verification code";

	private final EmailChallengeRepository challenges;
	private final EmailSender sender;
	private final EmailCodes codes;
	private final BlindIndex index;
	private final FieldCipher cipher;
	private final IdentityProperties properties;
	private final JdbcTemplate jdbc;
	private final TransactionTemplate transactions;
	private final TransactionTemplate ownTransaction;
	private final Clock clock;

	EmailVerificationService(EmailChallengeRepository challenges, EmailSender sender, EmailCodes codes,
			BlindIndex index, FieldCipher cipher, IdentityProperties properties, JdbcTemplate jdbc,
			TransactionTemplate transactions, Clock clock) {
		this.challenges = challenges;
		this.sender = sender;
		this.codes = codes;
		this.index = index;
		this.cipher = cipher;
		this.properties = properties;
		this.jdbc = jdbc;
		this.transactions = transactions;
		this.ownTransaction = new TransactionTemplate(java.util.Objects.requireNonNull(transactions.getTransactionManager()));
		this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.clock = clock;
	}

	@Override
	public String normalise(String email) {
		String value = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
		if (value.length() > 254 || !EMAIL.matcher(value).matches()) {
			throw ValidationException.field("email", "Enter a valid email address, for example name@example.com");
		}
		return value;
	}

	@Override
	public byte[] hash(String normalisedEmail) {
		return index.hash("email", normalisedEmail);
	}

	@Override
	public Sent send(UUID subjectId, String email, String clientIp) {
		String address = normalise(email);
		byte[] emailHash = hash(address);
		byte[] ipHash = clientIp == null ? null : index.hash("ip", clientIp);
		String code = codes.next(properties.otpLength());
		EmailChallenge reserved = transactions.execute(tx -> {
			Instant now = Instant.now(clock);
			lockGuard("EMAIL", emailHash, now);
			lockGuard("EMAIL_APP", index.hash("application", subjectId.toString()), now);
			if (ipHash != null) {
				lockGuard("EMAIL_IP", ipHash, now);
			}
			enforceLimits(subjectId, emailHash, ipHash, now);
			EmailChallenge challenge = challenges.save(new EmailChallenge(subjectId, cipher.encrypt(address), emailHash,
					ipHash, now.plus(properties.emailOtpTtl()), properties.otpMaxAttempts()));
			challenge.codeHash(codeHash(challenge.getId(), code));
			return challenge;
		});
		try {
			long minutes = properties.emailOtpTtl().toMinutes();
			sender.send(address, SUBJECT, "Your ELOKA verification code is " + code + ".\n\nIt is valid for " + minutes
					+ " minutes. Do not share it with anyone. If you did not ask for it, you can ignore this email.\n");
		}
		catch (RuntimeException ex) {
			transactions.executeWithoutResult(tx -> challenges.deleteById(reserved.getId()));
			throw ex;
		}
		return new Sent(reserved.getId().toString(), properties.emailOtpTtl().toSeconds(),
				properties.otpMinGap().toSeconds(), properties.otpLength(), Masking.email(address));
	}

	private sealed interface Check {

		record Right(Verified verified) implements Check {
		}

		record Wrong(int attemptsLeft) implements Check {
		}

		record NoLiveCode() implements Check {
		}
	}

	/**
	 * Runs in its own transaction, so a wrong guess is counted even when the caller's transaction
	 * (the application write) fails afterwards; the problem is raised only after it is saved.
	 */
	@Override
	public Verified verify(UUID subjectId, String challengeRef, String code) {
		UUID id = parse(challengeRef);
		String given = code == null ? "" : code.trim();
		Check check = ownTransaction.execute(tx -> {
			Instant now = Instant.now(clock);
			EmailChallenge challenge = id == null ? null
					: challenges.lock(id, subjectId).filter(c -> c.isLive(now)).orElse(null);
			if (challenge == null) {
				return new Check.NoLiveCode();
			}
			int left = challenge.reserveAttempt();
			if (!given.matches("\\d{" + properties.otpLength() + "}") || !challenge.matches(codeHash(challenge.getId(), given))) {
				return new Check.Wrong(left);
			}
			challenge.consume(now); // single use: the row lock makes a parallel second use see it consumed
			String address = cipher.decrypt(challenge.getEmailEnc());
			return new Check.Right(new Verified(address, challenge.getEmailHash(), Masking.email(address)));
		});
		return switch (check) {
			case Check.Right right -> right.verified();
			case Check.NoLiveCode none -> throw new ApiProblem(HttpStatus.GONE, "OTP_EXPIRED",
					"This code has expired. Please request a new one.");
			case Check.Wrong wrong when wrong.attemptsLeft() == 0 -> throw new ApiProblem(HttpStatus.TOO_MANY_REQUESTS,
					"OTP_LOCKED", "Too many wrong attempts. Please request a new code.");
			case Check.Wrong wrong -> throw new ApiProblem(HttpStatus.UNPROCESSABLE_CONTENT, "OTP_INVALID",
					"The code is not correct.", Map.of("attempts_left", wrong.attemptsLeft()));
		};
	}

	private byte[] codeHash(UUID challengeId, String code) {
		return index.hash("email-code", challengeId + ":" + code);
	}

	private static UUID parse(String ref) {
		try {
			return ref == null ? null : UUID.fromString(ref);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private void lockGuard(String kind, byte[] keyHash, Instant now) {
		jdbc.update("""
				INSERT INTO otp_send_guard (kind, key_hash, touched_at) VALUES (?, ?, ?)
				ON DUPLICATE KEY UPDATE touched_at = VALUES(touched_at)""", kind, keyHash,
				java.sql.Timestamp.from(now));
	}

	private void enforceLimits(UUID subjectId, byte[] emailHash, byte[] ipHash, Instant now) {
		challenges.findFirstByEmailHashOrderByCreatedAtDesc(emailHash)
			.map(EmailChallenge::getCreatedAt)
			.filter(last -> last.plus(properties.otpMinGap()).isAfter(now))
			.ifPresent(last -> {
				throw limited(Duration.between(now, last.plus(properties.otpMinGap())));
			});
		if (challenges.countByEmailHashAndCreatedAtAfter(emailHash, now.minus(Duration.ofHours(1))) >= properties.otpMaxPerHour()
				|| challenges.countBySubjectIdAndCreatedAtAfter(subjectId, now.minus(Duration.ofHours(1)))
						>= properties.otpMaxPerHour()) {
			throw limited(Duration.ofHours(1));
		}
		if (challenges.countByEmailHashAndCreatedAtAfter(emailHash, now.minus(Duration.ofDays(1))) >= properties.otpMaxPerDay()
				|| challenges.countBySubjectIdAndCreatedAtAfter(subjectId, now.minus(Duration.ofDays(1)))
						>= properties.otpMaxPerDay()) {
			throw limited(Duration.ofDays(1));
		}
		if (ipHash != null && challenges.countByClientIpHashAndCreatedAtAfter(ipHash, now.minus(Duration.ofHours(1)))
				>= properties.otpMaxPerIpPerHour()) {
			throw limited(Duration.ofHours(1));
		}
	}

	private static RateLimitedException limited(Duration retryAfter) {
		return new RateLimitedException("OTP_LIMIT_REACHED", "Too many code requests. Please try again later.",
				retryAfter);
	}
}
