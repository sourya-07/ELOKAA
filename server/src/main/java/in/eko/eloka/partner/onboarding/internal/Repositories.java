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

	Optional<PartnerApplication> findByPublicId(String publicId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from PartnerApplication a where a.publicId = :publicId")
	Optional<PartnerApplication> lockByPublicId(@Param("publicId") String publicId);

	/** For a staff change the applicant must see (unblocking a step): the row lock plus a new version. */
	@Lock(LockModeType.PESSIMISTIC_FORCE_INCREMENT)
	@Query("select a from PartnerApplication a where a.publicId = :publicId")
	Optional<PartnerApplication> lockByPublicIdAndBumpVersion(@Param("publicId") String publicId);

	/** Applications still with the applicant that have a step blocked after too many failures: staff follow up. */
	@Query("select a from PartnerApplication a where a.status in :statuses and exists (select s.id from ApplicationStep s "
			+ "where s.applicationId = a.id and s.status = in.eko.eloka.partner.onboarding.StepStatus.BLOCKED) "
			+ "order by a.lastApplicantActivityAt desc")
	List<PartnerApplication> findBlocked(@Param("statuses") Collection<ApplicationStatus> statuses,
			org.springframework.data.domain.Pageable page);

	/** The review queue, oldest submission first. */
	@Query("select a from PartnerApplication a where a.status in :statuses order by a.submittedAt asc, a.createdAt asc")
	List<PartnerApplication> findQueue(@Param("statuses") Collection<ApplicationStatus> statuses,
			org.springframework.data.domain.Pageable page);

	@Query("select a.id from PartnerApplication a where a.status = in.eko.eloka.partner.onboarding.ApplicationStatus.CORRECTION_REQUIRED and a.correctionDueAt < :now")
	List<UUID> findOverdueCorrections(@Param("now") Instant now);

	@Query("select a.id from PartnerApplication a where a.status in :statuses and a.lastApplicantActivityAt < :cutoff")
	List<UUID> findIdleIds(@Param("statuses") Collection<ApplicationStatus> statuses, @Param("cutoff") Instant cutoff);

	/** Another application that blocks a new one already holds this PAN. The DB unique key is the backstop. */
	@Query("select count(a) > 0 from PartnerApplication a where a.panHash = :hash and a.id <> :id and a.status in :blocking")
	boolean existsBlockingPan(@Param("hash") byte[] hash, @Param("id") UUID excludeId,
			@Param("blocking") Collection<ApplicationStatus> blocking);

	/** Another application that blocks a new one already holds this voter ID or driving licence (DB keys are the backstop). */
	@Query("select count(a) > 0 from PartnerApplication a where (a.voterIdHash = :hash or a.drivingLicenceHash = :hash) "
			+ "and a.id <> :id and a.status in :blocking")
	boolean existsBlockingIdDocument(@Param("hash") byte[] hash, @Param("id") UUID excludeId,
			@Param("blocking") Collection<ApplicationStatus> blocking);

	/** Another application that blocks a new one already has this email. The DB unique key is the backstop. */
	@Query("select count(a) > 0 from PartnerApplication a where a.emailHash = :hash and a.id <> :id and a.status in :blocking")
	boolean existsBlockingEmail(@Param("hash") byte[] hash, @Param("id") UUID excludeId,
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

interface ReviewActionRepository extends JpaRepository<ReviewAction, UUID> {

	List<ReviewAction> findByApplicationIdOrderByOccurredAtAsc(UUID applicationId);
}

interface PhotoLocationRepository extends JpaRepository<PhotoLocation, UUID> {

	Optional<PhotoLocation> findByDocumentId(UUID documentId);
}
