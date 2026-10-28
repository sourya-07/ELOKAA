package in.eko.eloka.partner.identity.internal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EmailChallengeRepository extends JpaRepository<EmailChallenge, UUID> {

	Optional<EmailChallenge> findFirstByEmailHashOrderByCreatedAtDesc(byte[] emailHash);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from EmailChallenge c where c.id = :id and c.subjectId = :subjectId")
	Optional<EmailChallenge> lock(@Param("id") UUID id, @Param("subjectId") UUID subjectId);

	long countByEmailHashAndCreatedAtAfter(byte[] emailHash, Instant since);

	long countByClientIpHashAndCreatedAtAfter(byte[] clientIpHash, Instant since);

	long countBySubjectIdAndCreatedAtAfter(UUID subjectId, Instant since);
}
