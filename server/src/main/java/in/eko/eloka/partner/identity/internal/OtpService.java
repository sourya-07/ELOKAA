package in.eko.eloka.partner.identity.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.partner.identity.IdentityProperties;
import in.eko.eloka.partner.integration.MobileOtpGateway;
import in.eko.eloka.partner.shared.crypto.BlindIndex;
import in.eko.eloka.partner.shared.error.RateLimitedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Issues and checks OTPs through {@link MobileOtpGateway}, with our own limits on top: a
 * minimum gap, per-mobile hourly and daily caps, a per-IP hourly cap, an expiry, a cap on wrong
 * guesses per OTP and single use. The code itself is never seen or stored here.
 *
 * <p>No database transaction stays open during a provider call. Sending: lock the mobile's and
 * IP's guard rows, check the limits and reserve the challenge (one short transaction), call the
 * provider, then record the reference. Checking: reserve an attempt, call the provider, then
 * consume the challenge, each step under the challenge's row lock.
 */
@Service
class OtpService {

	/** Outcome of a check. */
	sealed interface Check {

		record Valid() implements Check {
		}

		record Wrong(int attemptsLeft) implements Check {
		}

		record NoLiveOtp() implements Check {
		}
	}

	private record Attempt(UUID challengeId, String providerRef, int attemptsLeft) {
	}

	private final OtpChallengeRepository challenges;
	private final MobileOtpGateway gateway;
	private final BlindIndex index;
	private final IdentityProperties properties;
	private final JdbcTemplate jdbc;
	private final TransactionTemplate transactions;
	private final Clock clock;

	OtpService(OtpChallengeRepository challenges, MobileOtpGateway gateway, BlindIndex index,
			IdentityProperties properties, JdbcTemplate jdbc, TransactionTemplate transactions, Clock clock) {
		this.challenges = challenges;
		this.gateway = gateway;
		this.index = index;
		this.properties = properties;
		this.jdbc = jdbc;
		this.transactions = transactions;
		this.clock = clock;
	}

	/** A provider failure removes the reservation, so it does not count against the limits. */
	void issue(String purpose, String mobile, String clientIp) {
		byte[] mobileHash = index.hash("mobile", mobile);
		byte[] ipHash = clientIp == null ? null : index.hash("ip", clientIp);
		UUID reserved = transactions.execute(tx -> {
			Instant now = Instant.now(clock);
			lockGuard("MOBILE", mobileHash, now);
			if (ipHash != null) {
				lockGuard("IP", ipHash, now);
			}
			enforceLimits(purpose, mobileHash, ipHash, now);
			return challenges.save(new OtpChallenge(mobileHash, purpose, ipHash, now.plus(properties.otpTtl()),
					properties.otpMaxAttempts())).getId();
		});
		MobileOtpGateway.SentOtp sent;
		try {
			sent = gateway.send(mobile);
		}
		catch (RuntimeException ex) {
			transactions.executeWithoutResult(tx -> challenges.deleteById(reserved));
			throw ex;
		}
		transactions.executeWithoutResult(tx -> challenges.lockById(reserved)
			.ifPresent(challenge -> challenge.sent(sent.reference(), sent.expiresAt())));
	}

	/** Checks a code against the latest OTP for this mobile. */
	Check check(String purpose, String mobile, String code) {
		byte[] mobileHash = index.hash("mobile", mobile);
		String given = code == null ? "" : code.trim();
		Attempt attempt = transactions.execute(tx -> challenges.lockLatest(mobileHash, purpose)
			.filter(challenge -> challenge.isLive(Instant.now(clock)))
			.map(challenge -> new Attempt(challenge.getId(), challenge.getProviderRef(), challenge.reserveAttempt()))
			.orElse(null));
		if (attempt == null) {
			return new Check.NoLiveOtp();
		}
		boolean accepted;
		try {
			accepted = given.matches("\\d{" + properties.otpLength() + "}")
					&& gateway.verify(mobile, attempt.providerRef(), given);
		}
		catch (RuntimeException ex) {
			transactions.executeWithoutResult(tx -> challenges.lockById(attempt.challengeId())
				.ifPresent(OtpChallenge::releaseAttempt));
			throw ex;
		}
		if (!accepted) {
			return new Check.Wrong(attempt.attemptsLeft());
		}
		// Single use: of two parallel correct guesses, only the first consumes the OTP.
		Boolean consumed = transactions.execute(tx -> challenges.lockById(attempt.challengeId())
			.filter(challenge -> !challenge.isConsumed())
			.map(challenge -> {
				challenge.consume(Instant.now(clock));
				return true;
			})
			.orElse(false));
		return Boolean.TRUE.equals(consumed) ? new Check.Valid() : new Check.NoLiveOtp();
	}

	/** Creates the guard row if needed and holds an exclusive lock on it until the transaction ends. */
	private void lockGuard(String kind, byte[] keyHash, Instant now) {
		jdbc.update("""
				INSERT INTO otp_send_guard (kind, key_hash, touched_at) VALUES (?, ?, ?)
				ON DUPLICATE KEY UPDATE touched_at = VALUES(touched_at)""", kind, keyHash,
				java.sql.Timestamp.from(now));
	}

	private void enforceLimits(String purpose, byte[] mobileHash, byte[] ipHash, Instant now) {
		challenges.findFirstByMobileHashAndPurposeOrderByCreatedAtDesc(mobileHash, purpose)
			.map(OtpChallenge::getCreatedAt)
			.filter(last -> last.plus(properties.otpMinGap()).isAfter(now))
			.ifPresent(last -> {
				throw limited(Duration.between(now, last.plus(properties.otpMinGap())));
			});
		if (challenges.countByMobileHashAndCreatedAtAfter(mobileHash, now.minus(Duration.ofHours(1)))
				>= properties.otpMaxPerHour()) {
			throw limited(Duration.ofHours(1));
		}
		if (challenges.countByMobileHashAndCreatedAtAfter(mobileHash, now.minus(Duration.ofDays(1)))
				>= properties.otpMaxPerDay()) {
			throw limited(Duration.ofDays(1));
		}
		if (ipHash != null && challenges.countByClientIpHashAndCreatedAtAfter(ipHash, now.minus(Duration.ofHours(1)))
				>= properties.otpMaxPerIpPerHour()) {
			throw limited(Duration.ofHours(1));
		}
	}

	private static RateLimitedException limited(Duration retryAfter) {
		return new RateLimitedException("OTP_LIMIT_REACHED", "Too many OTP requests. Please try again later.",
				retryAfter);
	}
}
