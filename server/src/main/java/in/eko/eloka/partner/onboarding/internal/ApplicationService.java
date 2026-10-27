package in.eko.eloka.partner.onboarding.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import in.eko.eloka.partner.audit.AuditEntry;
import in.eko.eloka.partner.audit.AuditLog;
import in.eko.eloka.partner.eligibility.EligibilityApi;
import in.eko.eloka.partner.eligibility.EligibilityOutcome;
import in.eko.eloka.partner.onboarding.ApplicationStatus;
import in.eko.eloka.partner.onboarding.PartnerStep;
import in.eko.eloka.partner.onboarding.StepStatus;
import in.eko.eloka.partner.onboarding.VerificationMethod;
import in.eko.eloka.partner.shared.error.ApiProblem;
import in.eko.eloka.partner.shared.error.NotFoundException;
import in.eko.eloka.partner.shared.error.ValidationException;
import in.eko.eloka.partner.shared.persistence.PublicIds;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Applicant use cases on an application: start or resume, read, consent, method, declaration,
 * submit, expire. Every write goes through {@link #write}, which takes the row lock, checks the
 * {@code If-Match} version and that the application is still editable.
 */
@Service
class ApplicationService {

	private static final Set<ApplicationStatus> BLOCKING = EnumSet.copyOf(Arrays.stream(ApplicationStatus.values())
		.filter(ApplicationStatus::blocksNewApplication)
		.toList());

	/** The application to continue after sign-in, and whether it already existed. */
	record Start(ApplicationSnapshot snapshot, boolean resumed) {
	}

	private final PartnerApplicationRepository applications;
	private final ApplicationStepRepository steps;
	private final ConsentRecordRepository consents;
	private final TimelineEntryRepository timeline;
	private final ApplicationStateMachine stateMachine;
	private final OnboardingProperties properties;
	private final AuditLog audit;
	private final EligibilityApi eligibility;
	private final JsonMapper json;
	private final TransactionTemplate transactions;
	private final Clock clock;

	ApplicationService(PartnerApplicationRepository applications, ApplicationStepRepository steps,
			ConsentRecordRepository consents, TimelineEntryRepository timeline, ApplicationStateMachine stateMachine,
			OnboardingProperties properties, AuditLog audit, EligibilityApi eligibility, JsonMapper json,
			TransactionTemplate transactions, Clock clock) {
		this.applications = applications;
		this.steps = steps;
		this.consents = consents;
		this.timeline = timeline;
		this.stateMachine = stateMachine;
		this.properties = properties;
		this.audit = audit;
		this.eligibility = eligibility;
		this.json = json;
		this.transactions = transactions;
		this.clock = clock;
	}

	/**
	 * Continues the applicant's open application or starts one. An approved or ineligible
	 * application stops a new one (docs/OPEN_QUESTIONS.md Q-6). Two parallel starts for one
	 * applicant: the database's "one open application" key lets one win, the other resumes it.
	 */
	Start startOrResume(UUID applicantId) {
		try {
			return transactions.execute(tx -> startOrResumeOnce(applicantId));
		}
		catch (DataIntegrityViolationException raced) {
			return transactions.execute(tx -> startOrResumeOnce(applicantId));
		}
	}

	private Start startOrResumeOnce(UUID applicantId) {
		Optional<PartnerApplication> blocking = applications.findByApplicantIdOrderByCreatedAtDesc(applicantId)
			.stream()
			.filter(application -> application.getStatus().blocksNewApplication())
			.findFirst();
		if (blocking.isPresent()) {
			PartnerApplication existing = blocking.get();
			if (existing.getStatus() == ApplicationStatus.APPROVED) {
				throw new ApiProblem(HttpStatus.CONFLICT, "ALREADY_APPROVED", "You are already an approved ELOKA partner.");
			}
			// Open or ineligible: show it, so the applicant sees where they stand
			return new Start(snapshot(existing), true);
		}
		Instant now = Instant.now(clock);
		PartnerApplication application = applications
			.saveAndFlush(new PartnerApplication(PublicIds.next("ptn", now), applicantId, now, properties.idleExpiry()));
		for (PartnerStep key : PartnerStep.values()) {
			ApplicationStep step = new ApplicationStep(application.getId(), key, properties.maxAttempts());
			if (key == PartnerStep.MOBILE_VERIFICATION) {
				step.complete(json.writeValueAsString(Map.of("verified", true)), now); // the session proves it
			}
			steps.save(step);
		}
		timeline.save(new TimelineEntry(application.getId(), "APPLICATION_STARTED", null, null,
				ApplicationStatus.IN_PROGRESS, "APPLICANT", now));
		audit.record(new AuditEntry("APPLICATION_STARTED", "APPLICANT", applicantId.toString(), "PARTNER_APPLICATION",
				application.getPublicId(), Map.of()));
		return new Start(snapshot(application), false);
	}

	@Transactional(readOnly = true)
	Optional<ApplicationSnapshot> current(UUID applicantId) {
		return applications.findByApplicantIdOrderByCreatedAtDesc(applicantId).stream().findFirst().map(this::snapshot);
	}

	@Transactional(readOnly = true)
	ApplicationSnapshot get(UUID applicantId, String publicId) {
		return snapshot(find(applicantId, publicId));
	}

	@Transactional(readOnly = true)
	List<TimelineEntry> timeline(UUID applicantId, String publicId) {
		return timeline.findByApplicationIdOrderByOccurredAtAsc(find(applicantId, publicId).getId());
	}

	/**
	 * Runs one applicant write under the application row lock: version check ({@code 412} with
	 * the current snapshot), editable check, then {@code action}, then activity tracking.
	 *
	 * @param expectedVersion from {@code If-Match}; null only for writes that may run in parallel
	 *        (document uploads)
	 */
	@Transactional
	<T> T write(UUID applicantId, String publicId, Long expectedVersion, Function<StepContext, T> action) {
		PartnerApplication application = applications.lockByPublicIdAndApplicantId(publicId, applicantId)
			.orElseThrow(ApplicationService::notFound);
		if (expectedVersion != null && application.getVersion() != expectedVersion) {
			throw new ApiProblem(HttpStatus.PRECONDITION_FAILED, "PRECONDITION_FAILED",
					"This application changed in another tab or device. Here is the latest version.",
					Map.of("current", snapshot(application)));
		}
		if (!application.getStatus().isEditableByApplicant()) {
			throw new ApiProblem(HttpStatus.CONFLICT, "APPLICATION_NOT_EDITABLE",
					"The application can no longer be changed", Map.of("current_status", application.getStatus().name()));
		}
		Instant now = Instant.now(clock);
		StepContext context = new StepContext(application, steps.findByApplicationId(application.getId()), timeline, now);
		T result = action.apply(context);
		application.recordActivity(now, properties.idleExpiry());
		applications.saveAndFlush(application);
		return result;
	}

	/**
	 * Records a consent. The data-use consent completes the {@code CONSENT} step; DigiLocker and
	 * credit-check consents are checked when those steps run.
	 *
	 * @param clientIpHash blind index of the caller's IP, kept as evidence
	 */
	@Transactional
	ApplicationSnapshot consent(UUID applicantId, String publicId, long expectedVersion, String purposeName,
			String version, byte[] clientIpHash) {
		ConsentRecord.Purpose purpose = parse(ConsentRecord.Purpose.class, purposeName, "purpose");
		write(applicantId, publicId, expectedVersion, context -> {
			if (!properties.consentVersion().equals(version)) {
				throw new ApiProblem(HttpStatus.CONFLICT, "CONSENT_VERSION_OUTDATED",
						"The consent text has changed. Please read it again.",
						Map.of("current_version", properties.consentVersion()));
			}
			PartnerStep needs = switch (purpose) {
				case DATA_PROCESSING -> PartnerStep.CONSENT;
				case DIGILOCKER -> PartnerStep.AADHAAR;
				case CREDIT_CHECK -> PartnerStep.CREDIT_CHECK;
			};
			context.requirePrerequisites(needs);
			consents.save(new ConsentRecord(context.applicationId(), purpose, version, context.now(), clientIpHash));
			audit.record(new AuditEntry("CONSENT_GIVEN", "APPLICANT", applicantId.toString(), "PARTNER_APPLICATION",
					publicId, Map.of("purpose", purpose.name(), "version", version)));
			if (purpose == ConsentRecord.Purpose.DATA_PROCESSING) {
				context.complete(PartnerStep.CONSENT, json.writeValueAsString(Map.of("version", version)));
			}
			return null;
		});
		return get(applicantId, publicId);
	}

	/** Page 2: DigiLocker or step by step. Choosing the other one again resets the identity steps. */
	@Transactional
	ApplicationSnapshot chooseMethod(UUID applicantId, String publicId, long expectedVersion, String methodName) {
		VerificationMethod method = parse(VerificationMethod.class, methodName, "method");
		write(applicantId, publicId, expectedVersion, context -> {
			context.requirePrerequisites(PartnerStep.VERIFICATION_METHOD);
			if (method == context.application().getMethod()
					&& context.step(PartnerStep.VERIFICATION_METHOD).getStatus().isSatisfied()) {
				return null; // same choice again: nothing changes
			}
			context.application().chooseMethod(method);
			context.complete(PartnerStep.VERIFICATION_METHOD, json.writeValueAsString(Map.of("method", method.name())));
			return null;
		});
		return get(applicantId, publicId);
	}

	/** Final declaration of the current version; needs every other step done. */
	@Transactional
	ApplicationSnapshot declare(UUID applicantId, String publicId, long expectedVersion, String version,
			boolean accepted) {
		write(applicantId, publicId, expectedVersion, context -> {
			if (!accepted) {
				throw ValidationException.field("accepted", "Confirm the declaration to continue");
			}
			if (!properties.declarationVersion().equals(version)) {
				throw new ApiProblem(HttpStatus.CONFLICT, "DECLARATION_VERSION_OUTDATED",
						"The declaration has changed. Please read it again.",
						Map.of("current_version", properties.declarationVersion()));
			}
			context.requirePrerequisites(PartnerStep.DECLARATION);
			context.complete(PartnerStep.DECLARATION, json.writeValueAsString(Map.of("version", version)));
			return null;
		});
		return get(applicantId, publicId);
	}

	/** Submits when every step is satisfied; the steps are then frozen until a reviewer reopens one. */
	@Transactional
	ApplicationSnapshot submit(UUID applicantId, String publicId, long expectedVersion) {
		write(applicantId, publicId, expectedVersion, context -> {
			List<String> missing = context.steps()
				.values()
				.stream()
				.filter(step -> !step.getStatus().isSatisfied())
				.map(step -> step.getStepKey().name())
				.toList();
			if (!missing.isEmpty()) {
				throw new ApiProblem(HttpStatus.CONFLICT, "PREREQUISITE_INCOMPLETE", "Some steps are not complete yet",
						Map.of("missing_steps", missing));
			}
			// Checked again here, independent of the step status (ADR-0005)
			if (eligibility.latestOutcome(context.applicationId()) != EligibilityOutcome.ELIGIBLE) {
				throw new ApiProblem(HttpStatus.CONFLICT, "NOT_ELIGIBLE", "The credit check has not been passed");
			}
			context.steps().values().forEach(ApplicationStep::lock);
			stateMachine.apply(context.application(), LifecycleEvent.SUBMIT, "APPLICANT", applicantId.toString());
			context.application().correctionDue(null);
			return null;
		});
		return get(applicantId, publicId);
	}

	/**
	 * Withdraws after a fresh OTP (step-up), from in progress, submitted or correction required.
	 * Checked against the current version like every write.
	 */
	@Transactional
	ApplicationSnapshot withdraw(UUID applicantId, String publicId, long expectedVersion, String reasonCode) {
		PartnerApplication application = applications.lockByPublicIdAndApplicantId(publicId, applicantId)
			.orElseThrow(ApplicationService::notFound);
		if (application.getVersion() != expectedVersion) {
			throw new ApiProblem(HttpStatus.PRECONDITION_FAILED, "PRECONDITION_FAILED",
					"This application changed in another tab or device. Here is the latest version.",
					Map.of("current", snapshot(application)));
		}
		stateMachine.apply(application, LifecycleEvent.WITHDRAW, "APPLICANT", applicantId.toString());
		String reason = reasonCode == null || !reasonCode.trim().toUpperCase().matches("[A-Z][A-Z0-9_]{1,39}") ? "NONE"
				: reasonCode.trim().toUpperCase();
		audit.record(new AuditEntry("APPLICATION_WITHDRAW_REASON", "APPLICANT", applicantId.toString(),
				"PARTNER_APPLICATION", publicId, Map.of("reason_code", reason)));
		applications.saveAndFlush(application);
		return snapshot(application);
	}

	/**
	 * Expires one application (called by {@link ExpiryJob}, one transaction each). In progress:
	 * after the idle window. Waiting for a correction: on the correction's due date only.
	 */
	@Transactional
	boolean expireIfIdle(UUID applicationId) {
		PartnerApplication application = applications.lockById(applicationId).orElse(null);
		if (application == null) {
			return false;
		}
		Instant now = Instant.now(clock);
		boolean due = switch (application.getStatus()) {
			case IN_PROGRESS -> application.isIdleSince(now.minus(properties.idleExpiry()));
			case CORRECTION_REQUIRED -> application.isCorrectionOverdue(now);
			default -> false;
		};
		if (!due || !stateMachine.allows(application, LifecycleEvent.EXPIRE)) {
			return false;
		}
		stateMachine.apply(application, LifecycleEvent.EXPIRE, "SYSTEM", null);
		return true;
	}

	/** Candidates for {@link #expireIfIdle}. */
	List<UUID> idleApplicationIds() {
		Instant now = Instant.now(clock);
		List<UUID> ids = new ArrayList<>(
				applications.findIdleIds(EnumSet.of(ApplicationStatus.IN_PROGRESS), now.minus(properties.idleExpiry())));
		ids.addAll(applications.findOverdueCorrections(now));
		return ids;
	}

	Set<ApplicationStatus> blockingStatuses() {
		return BLOCKING;
	}

	PartnerApplication find(UUID applicantId, String publicId) {
		return applications.findByPublicIdAndApplicantId(publicId, applicantId).orElseThrow(ApplicationService::notFound);
	}

	ApplicationSnapshot snapshot(PartnerApplication application) {
		List<ApplicationStep> rows = new ArrayList<>(steps.findByApplicationId(application.getId()));
		rows.sort(java.util.Comparator.comparing(ApplicationStep::getStepKey));
		Map<PartnerStep, StepStatus> statusByKey = new EnumMap<>(PartnerStep.class);
		rows.forEach(step -> statusByKey.put(step.getStepKey(), step.getStatus()));

		String next = null;
		List<String> actions = new ArrayList<>();
		if (application.getStatus().isEditableByApplicant()) {
			next = rows.stream()
				.filter(step -> !step.getStatus().isSatisfied())
				.filter(step -> step.getStepKey().prerequisites().stream().allMatch(p -> statusByKey.get(p).isSatisfied()))
				.map(step -> step.getStepKey().name())
				.findFirst()
				.orElse(null);
			if (rows.stream().allMatch(step -> step.getStatus().isSatisfied())) {
				actions.add("SUBMIT");
			}
		}
		if (stateMachine.allows(application, LifecycleEvent.WITHDRAW)) {
			actions.add("WITHDRAW");
		}
		return new ApplicationSnapshot(application.getPublicId(), application.getStatus().name(),
				application.getVersion(), application.getMethod() == null ? null : application.getMethod().name(), next,
				application.getExpiresAt(), application.getSubmittedAt(), rows.stream().map(this::stepView).toList(),
				actions);
	}

	private ApplicationSnapshot.Step stepView(ApplicationStep step) {
		return new ApplicationSnapshot.Step(step.getStepKey().name(), step.getStatus().name(), step.getAttemptCount(),
				step.getMaxAttempts(), step.getLastErrorCode(), readJson(step.getSummary()), step.getReopenReasonCode(),
				step.getReviewerMessage());
	}

	private JsonNode readJson(String value) {
		return value == null ? null : json.readTree(value);
	}

	private static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
		try {
			return Enum.valueOf(type, value == null ? "" : value.trim().toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			throw ValidationException.field(field, "Choose one of " + Arrays.toString(type.getEnumConstants()));
		}
	}

	private static NotFoundException notFound() {
		return new NotFoundException("APPLICATION_NOT_FOUND", "Application not found");
	}
}
