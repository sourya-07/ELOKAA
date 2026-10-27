package in.eko.eloka.partner.identity.internal;

import java.time.Instant;
import in.eko.eloka.partner.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * One OTP sent to one mobile. The provider owns the code; this row holds the provider's
 * reference and our rules: expiry, attempt limit, single use. It is saved before the provider
 * is called (so it counts against the limits at once) and becomes live when the send is confirmed.
 */
@Entity
@Table(name = "otp_challenge")
class OtpChallenge extends BaseEntity {

	static final String PURPOSE_APPLICANT_SIGN_IN = "APPLICANT_SIGN_IN";
	static final String PURPOSE_STAFF_SIGN_IN = "STAFF_SIGN_IN";
	/** Step-up before withdrawing, to the applicant's own mobile. */
	static final String PURPOSE_REAUTH = "APPLICANT_REAUTH";
	/** Reference of a challenge for which no SMS was sent (unknown staff mobile): no code can match it. */
	static final String NOT_SENT = "NOT_SENT";

	/** Blind index of the mobile. */
	@Column(name = "mobile_hash", nullable = false, columnDefinition = "BINARY(32)")
	private byte[] mobileHash;

	@Column(nullable = false, length = 20)
	private String purpose;

	@Column(name = "provider_ref", length = 64)
	private String providerRef;

	@Column(name = "client_ip_hash", columnDefinition = "BINARY(32)")
	private byte[] clientIpHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "max_attempts", nullable = false)
	private int maxAttempts;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	protected OtpChallenge() {
	}

	/** A reserved send: not live until {@link #sent} records the provider's reference. */
	OtpChallenge(byte[] mobileHash, String purpose, byte[] clientIpHash, Instant expiresAt, int maxAttempts) {
		this.mobileHash = mobileHash;
		this.purpose = purpose;
		this.clientIpHash = clientIpHash;
		this.expiresAt = expiresAt;
		this.maxAttempts = maxAttempts;
	}

	void sent(String reference, Instant providerExpiry) {
		this.providerRef = reference;
		if (providerExpiry != null && providerExpiry.isBefore(expiresAt)) {
			this.expiresAt = providerExpiry;
		}
	}

	boolean isLive(Instant now) {
		return providerRef != null && consumedAt == null && now.isBefore(expiresAt) && attempts < maxAttempts;
	}

	String getProviderRef() {
		return providerRef;
	}

	/** Uses up one attempt before the code is checked. @return attempts left after this one */
	int reserveAttempt() {
		attempts++;
		return Math.max(0, maxAttempts - attempts);
	}

	/** Gives back an attempt the provider could not judge (it was unreachable). */
	void releaseAttempt() {
		attempts = Math.max(0, attempts - 1);
	}

	boolean isConsumed() {
		return consumedAt != null;
	}

	void consume(Instant now) {
		this.consumedAt = now;
	}
}
