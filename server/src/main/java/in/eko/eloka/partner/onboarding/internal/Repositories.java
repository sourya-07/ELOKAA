package in.eko.eloka.partner.onboarding.internal;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import in.eko.eloka.partner.onboarding.ApplicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PartnerApplicationRepository extends JpaRepository<PartnerApplication, UUID> {

	Optional<PartnerApplication> findByPublicIdAndApplicantId(String publicId, UUID applicantId);

	List<PartnerApplication> findByApplicantIdOrderByCreatedAtDesc(UUID applicantId);

	/** Write path: one writer per application at a time. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from PartnerApplication a where a.publicId = :publicId and a.applicantId = :applicantId")
	Optional<PartnerApplication> lockByPublicIdAndApplicantId(@Param("publicId") String publicId,
			@Param("applicantId") UUID applicantId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from PartnerApplication a where a.id = :id")
	Optional<PartnerApplication> lockById(@Param("id") UUID id);

	@Query("select a.id from PartnerApplication a where a.status = in.eko.eloka.partner.onboarding.ApplicationStatus.CORRECTION_REQUIRED and a.correctionDueAt < :now")
	List<UUID> findOverdueCorrections(@Param("now") Instant now);

	@Query("select a.id from PartnerApplication a where a.status in :statuses and a.lastApplicantActivityAt < :cutoff")
	List<UUID> findIdleIds(@Param("statuses") Collection<ApplicationStatus> statuses, @Param("cutoff") Instant cutoff);

	/** Another application that blocks a new one already holds this PAN. The DB unique key is the backstop. */
	@Query("select count(a) > 0 from PartnerApplication a where a.panHash = :hash and a.id <> :id and a.status in :blocking")
	boolean existsBlockingPan(@Param("hash") byte[] hash, @Param("id") UUID excludeId,
			@Param("blocking") Collection<ApplicationStatus> blocking);
}

interface ApplicationStepRepository extends JpaRepository<ApplicationStep, UUID> {

	List<ApplicationStep> findByApplicationId(UUID applicationId);
}

interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {

	Optional<ConsentRecord> findFirstByApplicationIdAndPurposeOrderByAcceptedAtDesc(UUID applicationId,
			ConsentRecord.Purpose purpose);
}

interface TimelineEntryRepository extends JpaRepository<TimelineEntry, UUID> {

	List<TimelineEntry> findByApplicationIdOrderByOccurredAtAsc(UUID applicationId);
}

interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

	Optional<IdempotencyRecord> findByScopeIdAndIdemKey(UUID scopeId, String idemKey);

	@Modifying
	@Query("delete from IdempotencyRecord r where r.expiresAt < :now")
	int deleteExpired(@Param("now") Instant now);
}

interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, UUID> {

	List<ApplicationDocument> findByApplicationIdAndRemovedAtIsNullOrderByCreatedAtAsc(UUID applicationId);

	Optional<ApplicationDocument> findByDocumentIdAndApplicationId(UUID documentId, UUID applicationId);
}

interface PartnerAddressRepository extends JpaRepository<PartnerAddress, UUID> {

	Optional<PartnerAddress> findByApplicationId(UUID applicationId);
}

interface IdNumberRepository extends JpaRepository<IdNumber, UUID> {

	List<IdNumber> findByApplicationId(UUID applicationId);

	Optional<IdNumber> findByApplicationIdAndType(UUID applicationId, IdNumber.Type type);
}
