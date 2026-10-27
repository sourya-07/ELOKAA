package in.eko.eloka.partner.identity.internal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ApplicantRepository extends JpaRepository<Applicant, UUID> {

	Optional<Applicant> findByMobileHash(byte[] mobileHash);
}

interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

	Optional<OtpChallenge> findFirstByMobileHashAndPurposeOrderByCreatedAtDesc(byte[] mobileHash, String purpose);

	/** The latest challenge, locked: two parallel guesses cannot both take the last attempt. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select c from OtpChallenge c where c.mobileHash = :mobileHash and c.purpose = :purpose
			order by c.createdAt desc limit 1""")
	Optional<OtpChallenge> lockLatest(@Param("mobileHash") byte[] mobileHash, @Param("purpose") String purpose);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from OtpChallenge c where c.id = :id")
	Optional<OtpChallenge> lockById(@Param("id") UUID id);

	long countByMobileHashAndCreatedAtAfter(byte[] mobileHash, Instant since);

	long countByClientIpHashAndCreatedAtAfter(byte[] clientIpHash, Instant since);
}

interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

	Optional<AuthSession> findByAccessTokenHash(byte[] hash);

	Optional<AuthSession> findByRefreshTokenHash(byte[] hash);

	Optional<AuthSession> findByPreviousRefreshHash(byte[] hash);

	/**
	 * Slides the idle window without the entity's version check: a browser fires several calls
	 * in parallel on one token, and they must not fail each other with an optimistic-lock error.
	 * Only one of them changes the row (the {@code lastSeenAt < touchedBefore} guard).
	 */
	@Modifying
	@Query("""
			update AuthSession s set s.lastSeenAt = :now,
			    s.idleExpiresAt = case when :idleUntil < s.absoluteExpiresAt then :idleUntil else s.absoluteExpiresAt end
			where s.id = :id and s.lastSeenAt < :touchedBefore and s.revokedAt is null""")
	int touch(@Param("id") UUID id, @Param("now") Instant now, @Param("idleUntil") Instant idleUntil,
			@Param("touchedBefore") Instant touchedBefore);
}
