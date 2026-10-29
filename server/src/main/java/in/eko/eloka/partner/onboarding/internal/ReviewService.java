package in.eko.eloka.partner.onboarding.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.eko.eloka.partner.audit.AuditEntry;
import in.eko.eloka.partner.audit.AuditLog;
import in.eko.eloka.partner.audit.AuditTrail;
import in.eko.eloka.partner.documents.DocumentApi;
import in.eko.eloka.partner.documents.DocumentContent;
import in.eko.eloka.partner.eligibility.EligibilityApi;
import in.eko.eloka.partner.eligibility.EligibilityOutcome;
import in.eko.eloka.partner.identity.StaffDirectory;
import in.eko.eloka.partner.identity.StaffView;
import in.eko.eloka.partner.onboarding.ApplicationStatus;
import in.eko.eloka.partner.onboarding.PartnerStep;
import in.eko.eloka.partner.onboarding.StepStatus;
import in.eko.eloka.partner.shared.crypto.FieldCipher;
import in.eko.eloka.partner.shared.error.ApiProblem;
import in.eko.eloka.partner.shared.error.ForbiddenException;
import in.eko.eloka.partner.shared.error.NotFoundException;
import in.eko.eloka.partner.shared.error.ValidationException;
import in.eko.eloka.partner.shared.security.Caller;
import in.eko.eloka.partner.shared.security.Role;
import in.eko.eloka.partner.verification.EvidenceStatus;
import in.eko.eloka.partner.verification.VerificationApi;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff review of submitted applications. Reviewers claim a case, decide each piece of evidence,
 * ask for corrections and recommend; approvers approve or reject. An approval needs every piece
 * of evidence verified, a passed credit check, a reviewer's "approve" recommendation, and an
 * approver who is not that reviewer (maker-checker). Every action is in the audit log.
 */
@Service
class ReviewService {

	private static final Set<ApplicationStatus> QUEUE = EnumSet.of(ApplicationStatus.SUBMITTED,
			ApplicationStatus.UNDER_REVIEW, ApplicationStatus.ON_HOLD);
	/** Steps a reviewer may send back; the rest are system steps (the credit check is redone only through PAN). */
	private static final Set<PartnerStep> CORRECTABLE = EnumSet.of(PartnerStep.EMAIL_VERIFICATION, PartnerStep.AADHAAR,
			PartnerStep.PAN, PartnerStep.HOME_ADDRESS, PartnerStep.ALTERNATE_ID, PartnerStep.PHOTOS, PartnerStep.KYC_DOCUMENTS,
			PartnerStep.EDUCATION);
	private static final Duration DEFAULT_CORRECTION_WINDOW = Duration.ofDays(14);

	record QueueItem(@JsonProperty("application_id") String applicationId,
			@JsonProperty("application_number") String applicationNumber, String status, String method,
			@JsonProperty("submitted_at") Instant submittedAt, @JsonProperty("assigned_to") String assignedTo,
			@JsonProperty("waiting_items") int waitingItems) {
	}

	record EvidenceItem(String id, String type, String kind, String status, String value,
			@JsonProperty("reason_code") String reasonCode) {
	}

	record ActionView(String action, String staff, String decision, @JsonProperty("reason_code") String reasonCode,
			String note, @JsonProperty("occurred_at") Instant occurredAt) {
	}

	record CaseView(ApplicationSnapshot application, @JsonProperty("assigned_to") String assignedTo,
			@JsonProperty("assigned_to_id") String assignedToId,
			String eligibility, List<EvidenceItem> evidence, Map<String, String> address,
			@JsonProperty("id_numbers") Map<String, String> idNumbers, List<ActionView> actions,
			@JsonProperty("approval_blockers") List<String> approvalBlockers,
			@JsonProperty("photo_location") PhotoLocationView photoLocation,
			@JsonProperty("correctable_steps") List<String> correctableSteps) {
	}

	/** Where the live photo was taken, for the reviewer; the browser's word, not proof (Q-3). */
	record PhotoLocationView(double latitude, double longitude, @JsonProperty("accuracy_m") long accuracyM,
			@JsonProperty("captured_at") Instant capturedAt, String flags) {
	}

	private final PartnerApplicationRepository applications;
	private final ApplicationService applicationService;
	private final ApplicationStepRepository steps;
	private final ApplicationDocumentRepository documents;
	private final PartnerAddressRepository addresses;
	private final IdNumberRepository idNumbers;
	private final PhotoLocationRepository locations;
	private final TimelineEntryRepository timeline;
	private final ReviewActionRepository actions;
	private final ApplicationStateMachine stateMachine;
	private final VerificationApi verification;
	private final EligibilityApi eligibility;
	private final DocumentApi documentApi;
	private final StaffDirectory staff;
	private final AuditLog audit;
	private final AuditTrail auditTrail;
	private final FieldCipher cipher;
	private final Clock clock;

	ReviewService(PartnerApplicationRepository applications, ApplicationService applicationService,
			ApplicationStepRepository steps, ApplicationDocumentRepository documents, PartnerAddressRepository addresses,
			IdNumberRepository idNumbers, PhotoLocationRepository locations, TimelineEntryRepository timeline,
			ReviewActionRepository actions,
			ApplicationStateMachine stateMachine, VerificationApi verification, EligibilityApi eligibility,
			DocumentApi documentApi, StaffDirectory staff, AuditLog audit, AuditTrail auditTrail, FieldCipher cipher,
			Clock clock) {
		this.applications = applications;
		this.applicationService = applicationService;
		this.steps = steps;
		this.documents = documents;
		this.addresses = addresses;
		this.idNumbers = idNumbers;
		this.locations = locations;
		this.timeline = timeline;
		this.actions = actions;
		this.stateMachine = stateMachine;
		this.verification = verification;
		this.eligibility = eligibility;
		this.documentApi = documentApi;
		this.staff = staff;
		this.audit = audit;
		this.auditTrail = auditTrail;
		this.cipher = cipher;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	List<QueueItem> queue(Caller caller, String status, int page, int size) {
		requireAnyRole(caller);
		PageRequest paging = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
		// "BLOCKED": applications still with the applicant that need staff to follow up
		List<PartnerApplication> found = "BLOCKED".equalsIgnoreCase(status)
				? applications.findBlocked(EnumSet.of(ApplicationStatus.IN_PROGRESS, ApplicationStatus.CORRECTION_REQUIRED), paging)
				: applications.findQueue(status == null || status.isBlank() ? QUEUE
						: EnumSet.of(parse(ApplicationStatus.class, status, "status")), paging);
		return found
			.stream()
			.map(a -> new QueueItem(a.getPublicId(), ApplicationService.number(a), a.getStatus().name(), a.getMethod() == null ? null : a.getMethod().name(),
					a.getSubmittedAt(), staffName(a.getAssignedStaffId()), blockers(a).size()))
			.toList();
	}

	@Transactional(readOnly = true)
	CaseView caseView(Caller caller, String publicId) {
		requireAnyRole(caller);
		PartnerApplication application = find(publicId);
		UUID id = application.getId();
		List<EvidenceItem> evidence = new ArrayList<>();
		verification.evidence(id).forEach(e -> evidence.add(new EvidenceItem(e.id().toString(), "VERIFICATION",
				e.kind().name() + "/" + e.method().name(), e.status().name(), e.valueMasked(), e.failureCode())));
		documents.findByApplicationIdAndRemovedAtIsNullOrderByCreatedAtAsc(id)
			.forEach(d -> evidence.add(new EvidenceItem(d.getDocumentId().toString(), "DOCUMENT", d.getKind().name(),
					d.getStatus().name(), null, d.getRejectReason())));
		Map<String, String> address = addresses.findByApplicationId(id)
			.map(a -> Map.of("state", a.getState(), "pincode", a.getPincode(), "source", a.getSource().name(), "status",
					a.getStatus().name()))
			.orElse(Map.of());
		Map<String, String> numbers = new LinkedHashMap<>();
		idNumbers.findByApplicationId(id).forEach(n -> numbers.put(n.getType().name(), n.getNumberMasked())); // before journey v2
		verification.evidence(id).stream()
			.filter(e -> e.status() == EvidenceStatus.VERIFIED
					&& (e.kind() == VerificationApi.Kind.VOTER_ID || e.kind() == VerificationApi.Kind.DRIVING_LICENCE))
			.forEach(e -> numbers.put(e.kind().name(), e.valueMasked()));
		List<ActionView> history = actions.findByApplicationIdOrderByOccurredAtAsc(id)
			.stream()
			.map(a -> new ActionView(a.getAction(), staffName(a.getStaffId()), a.getDecision(), a.getReasonCode(),
					a.getNote(), a.getOccurredAt()))
			.toList();
		UUID assigned = application.getAssignedStaffId();
		return new CaseView(applicationService.snapshot(application), staffName(assigned),
				assigned == null ? null : assigned.toString(),
				eligibility.latestOutcome(id).name(), evidence, address, numbers, history, blockers(application),
				photoLocation(id), CORRECTABLE.stream().sorted().map(Enum::name).toList());
	}

	/**
	 * Gives a blocked step its tries back after staff followed up with the applicant (D5). Only while
	 * the application is with the applicant; a new credit check is a new bureau enquiry, so only an
	 * approver may unblock that one. The applicant's version changes, so an open page reloads.
	 */
	@Transactional
	CaseView unblock(Caller caller, String publicId, String stepName, String reasonCode, String note) {
		requireAnyRole(caller);
		PartnerStep key = parse(PartnerStep.class, stepName, "step");
		if (key == PartnerStep.CREDIT_CHECK) {
			requireRole(caller, Role.APPROVER);
		}
		String reason = code(reasonCode);
		PartnerApplication application = applications.lockByPublicIdAndBumpVersion(publicId).orElseThrow(ReviewService::notFound);
		if (!application.getStatus().isEditableByApplicant()) {
			throw new ApiProblem(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION", "Only an application with the applicant can be unblocked",
					Map.of("current_status", application.getStatus().name()));
		}
		ApplicationStep step = steps.findByApplicationId(application.getId())
			.stream()
			.filter(s -> s.getStepKey() == key)
			.findFirst()
			.orElseThrow(() -> new NotFoundException("STEP_NOT_FOUND", "This application has no such step"));
		Instant now = Instant.now(clock);
		step.unblock(now);
		timeline.save(new TimelineEntry(application.getId(), "STEP_UNBLOCKED", key, null, null, "STAFF", now));
		record(application, caller, "UNBLOCK", null, reason, note, key.name());
		applications.saveAndFlush(application);
		return caseView(caller, publicId);
	}

	private PhotoLocationView photoLocation(UUID applicationId) {
		return documents.findByApplicationIdAndRemovedAtIsNullOrderByCreatedAtAsc(applicationId)
			.stream()
			.filter(d -> d.getKind() == in.eko.eloka.partner.documents.DocumentKind.LIVE_PHOTO)
			.findFirst()
			.flatMap(d -> locations.findByDocumentId(d.getDocumentId()))
			.map(l -> new PhotoLocationView(Double.parseDouble(cipher.decrypt(l.getLatitudeEnc())),
					Double.parseDouble(cipher.decrypt(l.getLongitudeEnc())), Math.round(l.getAccuracyM()), l.getCapturedAt(),
					l.getFlags()))
			.orElse(null);
	}

	@Transactional
	CaseView claim(Caller caller, String publicId) {
		requireRole(caller, Role.REVIEWER);
		return act(caller, publicId, application -> {
			stateMachine.apply(application, LifecycleEvent.CLAIM, "STAFF", caller.subjectId().toString());
			application.assignTo(caller.subjectId());
			record(application, caller, "CLAIM", null, null, null, null);
		});
	}

	@Transactional
	CaseView release(Caller caller, String publicId) {
		requireRole(caller, Role.REVIEWER);
		return act(caller, publicId, application -> {
			requireAssigned(application, caller);
			stateMachine.apply(application, LifecycleEvent.RELEASE, "STAFF", caller.subjectId().toString());
			application.assignTo(null);
			record(application, caller, "RELEASE", null, null, null, null);
		});
	}

	@Transactional
	CaseView decideDocument(Caller caller, String publicId, UUID documentId, boolean verified, String reasonCode) {
		requireRole(caller, Role.REVIEWER);
		return act(caller, publicId, application -> {
			requireReviewing(application, caller);
			ApplicationDocument document = documents.findByDocumentIdAndApplicationId(documentId, application.getId())
				.filter(ApplicationDocument::isActive)
				.orElseThrow(() -> new NotFoundException("DOCUMENT_NOT_FOUND", "Document not found"));
			document.decide(verified, reason(verified, reasonCode), Instant.now(clock));
			record(application, caller, "DOCUMENT_DECISION", verified ? "VERIFIED" : "REJECTED", reason(verified, reasonCode),
					null, documentId.toString());
		});
	}

	@Transactional
	CaseView decideVerification(Caller caller, String publicId, UUID evidenceId, boolean verified, String reasonCode) {
		requireRole(caller, Role.REVIEWER);
		return act(caller, publicId, application -> {
			requireReviewing(application, caller);
			verification.decide(application.getId(), evidenceId, verified, reason(verified, reasonCode));
			record(application, caller, "VERIFICATION_DECISION", verified ? "VERIFIED" : "REJECTED",
					reason(verified, reasonCode), null, evidenceId.toString());
		});
	}

	@Transactional
	CaseView decideAddress(Caller caller, String publicId, boolean verified, String reasonCode) {
		requireRole(caller, Role.REVIEWER);
		return act(caller, publicId, application -> {
			requireReviewing(application, caller);
			PartnerAddress address = addresses.findByApplicationId(application.getId())
				.orElseThrow(() -> new NotFoundException("ADDRESS_NOT_FOUND", "No address on this application"));
			String reason = reason(verified, reasonCode);
			address.decide(verified);
			record(application, caller, "ADDRESS_DECISION", verified ? "VERIFIED" : "REJECTED", reason, null, null);
		});
	}

	/** Sends steps back to the applicant with a reason; the declaration is always asked for again. */
	@Transactional
	CaseView requestCorrection(Caller caller, String publicId, List<String> stepNames, String reasonCode,
			String message, Integer dueInDays) {
		requireRole(caller, Role.REVIEWER);
		if (message == null || message.isBlank()) {
			throw ValidationException.field("message", "Say what the applicant must fix");
		}
		String correctionCode = code(reasonCode);
		if (stepNames == null || stepNames.isEmpty()) {
			throw ValidationException.field("steps", "Choose at least one step to send back");
		}
		List<PartnerStep> chosen = stepNames.stream().map(name -> parse(PartnerStep.class, name, "steps")).toList();
		if (!CORRECTABLE.containsAll(chosen)) {
			throw ValidationException.field("steps", "Only " + CORRECTABLE + " can be sent back");
		}
		Duration window = dueInDays == null ? DEFAULT_CORRECTION_WINDOW : Duration.ofDays(Math.min(Math.max(dueInDays, 1), 30));
		return act(caller, publicId, application -> {
			requireReviewing(application, caller);
			Map<PartnerStep, ApplicationStep> byKey = new LinkedHashMap<>();
			steps.findByApplicationId(application.getId()).forEach(step -> byKey.put(step.getStepKey(), step));
			for (PartnerStep key : chosen) {
				byKey.get(key).reopen(correctionCode, message.length() > 1000 ? message.substring(0, 1000) : message);
			}
			byKey.get(PartnerStep.DECLARATION).reopen(StepContext.DEPENDENCY_CHANGED, null);
			stateMachine.apply(application, LifecycleEvent.REQUEST_CORRECTION, "STAFF", caller.subjectId().toString());
			application.assignTo(null);
			application.correctionDue(Instant.now(clock).plus(window));
			record(application, caller, "REQUEST_CORRECTION", null, correctionCode, message, chosen.toString());
		});
	}

	@Transactional
	CaseView recommend(Caller caller, String publicId, String decision, String note) {
		requireRole(caller, Role.REVIEWER);
		String value = decision == null ? "" : decision.trim().toUpperCase();
		if (!value.equals("APPROVE") && !value.equals("REJECT")) {
			throw ValidationException.field("decision", "Recommend APPROVE or REJECT");
		}
		return act(caller, publicId, application -> {
			requireReviewing(application, caller);
			if (value.equals("APPROVE") && !blockers(application).isEmpty()) {
				throw new ApiProblem(HttpStatus.CONFLICT, "NOT_READY", "Some items are not verified yet",
						Map.of("approval_blockers", blockers(application)));
			}
			record(application, caller, ReviewAction.RECOMMEND, value, null, note, null);
		});
	}

	@Transactional
	CaseView approve(Caller caller, String publicId, String note) {
		requireRole(caller, Role.APPROVER);
		return act(caller, publicId, application -> {
			ReviewAction recommendation = latestRecommendation(application)
				.filter(r -> "APPROVE".equals(r.getDecision()))
				.orElseThrow(() -> new ApiProblem(HttpStatus.CONFLICT, "NO_RECOMMENDATION",
						"A reviewer must recommend approval first"));
			if (recommendation.getStaffId().equals(caller.subjectId())) {
				throw new ForbiddenException("SAME_PERSON", "The person who recommended cannot also approve");
			}
			List<String> blockers = blockers(application);
			if (!blockers.isEmpty()) {
				throw new ApiProblem(HttpStatus.CONFLICT, "NOT_READY", "Some items are not verified yet",
						Map.of("approval_blockers", blockers));
			}
			stateMachine.apply(application, LifecycleEvent.APPROVE, "STAFF", caller.subjectId().toString());
			application.assignTo(null);
			record(application, caller, "APPROVE", "APPROVE", null, note, null);
		});
	}

	@Transactional
	CaseView reject(Caller caller, String publicId, String reasonCode, String note) {
		requireRole(caller, Role.APPROVER);
		String rejectCode = code(reasonCode);
		return act(caller, publicId, application -> {
			stateMachine.apply(application, LifecycleEvent.REJECT, "STAFF", caller.subjectId().toString());
			application.assignTo(null);
			record(application, caller, "REJECT", "REJECT", rejectCode, note, null);
		});
	}

	@Transactional
	CaseView hold(Caller caller, String publicId, String reasonCode) {
		requireAnyRole(caller);
		String holdCode = code(reasonCode);
		return act(caller, publicId, application -> {
			stateMachine.apply(application, LifecycleEvent.HOLD, "STAFF", caller.subjectId().toString());
			record(application, caller, "HOLD", null, holdCode, null, null);
		});
	}

	@Transactional
	CaseView releaseHold(Caller caller, String publicId) {
		requireAnyRole(caller);
		return act(caller, publicId, application -> {
			stateMachine.apply(application, LifecycleEvent.RELEASE_HOLD, "STAFF", caller.subjectId().toString());
			record(application, caller, "RELEASE_HOLD", null, null, null, null);
		});
	}

	/** Shows one hidden value in full. Approvers only, with a reason, always audited. */
	@Transactional
	Map<String, String> revealPii(Caller caller, String publicId, String field, String reason) {
		requireRole(caller, Role.APPROVER);
		if (reason == null || reason.trim().length() < 5) {
			throw ValidationException.field("reason", "Say why you need to see this");
		}
		PartnerApplication application = find(publicId);
		String value = switch (field == null ? "" : field.trim().toUpperCase()) {
			case "PAN" -> verification.revealPan(application.getId()).orElse(null);
			case "ADDRESS" -> addresses.findByApplicationId(application.getId())
				.map(a -> String.join(", ", java.util.stream.Stream.of(cipher.decrypt(a.getLine1Enc()),
						a.getLine2Enc() == null ? null : cipher.decrypt(a.getLine2Enc()), cipher.decrypt(a.getCityEnc()),
						a.getDistrict(), a.getState(), a.getPincode()).filter(java.util.Objects::nonNull).toList()))
				.orElse(null);
			default -> throw ValidationException.field("field", "Choose PAN or ADDRESS");
		};
		if (value == null) {
			throw new NotFoundException("NOTHING_TO_SHOW", "This application has no such value");
		}
		String name = field.trim().toUpperCase();
		// The reason is free text and may name people: it stays in the staff review history, not in the audit log
		actions.save(new ReviewAction(application.getId(), caller.subjectId(), "PII_REVEAL", null, null,
				reason.trim().length() > 1000 ? reason.trim().substring(0, 1000) : reason.trim(), name, Instant.now(clock)));
		audit.record(new AuditEntry("PII_REVEALED", "STAFF", caller.subjectId().toString(), "PARTNER_APPLICATION", publicId,
				Map.of("field", name)));
		return Map.of("field", name, "value", value);
	}

	/** A file for review. Every view is audited. */
	@Transactional
	DocumentContent documentContent(Caller caller, String publicId, UUID documentId) {
		requireAnyRole(caller);
		PartnerApplication application = find(publicId);
		ApplicationDocument link = documents.findByDocumentIdAndApplicationId(documentId, application.getId())
			.orElseThrow(() -> new NotFoundException("DOCUMENT_NOT_FOUND", "Document not found"));
		// Full Aadhaar images: the reviewer on the case or an approver only (ADR-0008)
		if (link.getKind().isFullAadhaar() && !caller.hasRole(Role.APPROVER)
				&& !caller.subjectId().equals(application.getAssignedStaffId())) {
			throw new ForbiddenException("NOT_ASSIGNED", "Claim the case to see the Aadhaar images");
		}
		audit.record(new AuditEntry("DOCUMENT_VIEWED", "STAFF", caller.subjectId().toString(), "PARTNER_APPLICATION",
				publicId, Map.of("document", documentId.toString())));
		return documentApi.loadForReview(documentId);
	}

	@Transactional(readOnly = true)
	List<AuditTrail.AuditRecord> auditTrail(Caller caller, String publicId) {
		requireAnyRole(caller);
		find(publicId);
		return auditTrail.forTarget("PARTNER_APPLICATION", List.of(publicId), 500);
	}

	/** What still stops an approval; empty when the application can be approved. */
	List<String> blockers(PartnerApplication application) {
		List<String> blockers = new ArrayList<>();
		UUID id = application.getId();
		if (eligibility.latestOutcome(id) != EligibilityOutcome.ELIGIBLE) {
			blockers.add("CREDIT_CHECK_NOT_PASSED");
		}
		Map<VerificationApi.Kind, EvidenceStatus> checks = new java.util.EnumMap<>(VerificationApi.Kind.class);
		verification.evidence(id).forEach(e -> checks.put(e.kind(), e.status()));
		for (VerificationApi.Kind kind : List.of(VerificationApi.Kind.PAN, VerificationApi.Kind.AADHAAR)) {
			if (checks.get(kind) != EvidenceStatus.VERIFIED) {
				blockers.add(kind.name() + "_NOT_VERIFIED");
			}
		}
		// One verified voter ID or driving licence is enough. Applications submitted before journey v2 got the
		// step frozen without a result (V14: LOCKED, no summary): their numbers were typed and checked by staff
		boolean hasAlternateStep = steps.findByApplicationId(id).stream()
			.anyMatch(s -> s.getStepKey() == PartnerStep.ALTERNATE_ID && s.getStatus() != StepStatus.NOT_APPLICABLE
					&& !(s.getStatus() == StepStatus.LOCKED && s.getSummary() == null));
		if (hasAlternateStep && verification.verifiedAlternateIds(id).isEmpty()) {
			blockers.add("ALTERNATE_ID_NOT_VERIFIED");
		}
		if (addresses.findByApplicationId(id).map(a -> a.getStatus() != EvidenceStatus.VERIFIED).orElse(true)) {
			blockers.add("ADDRESS_NOT_VERIFIED");
		}
		documents.findByApplicationIdAndRemovedAtIsNullOrderByCreatedAtAsc(id)
			.stream()
			.filter(d -> d.getStatus() != EvidenceStatus.VERIFIED)
			.forEach(d -> blockers.add(d.getKind().name() + "_NOT_VERIFIED"));
		steps.findByApplicationId(id)
			.stream()
			.filter(s -> s.getStatus() != StepStatus.LOCKED)
			.forEach(s -> blockers.add(s.getStepKey().name() + "_NOT_SUBMITTED"));
		return blockers;
	}

	private CaseView act(Caller caller, String publicId, java.util.function.Consumer<PartnerApplication> action) {
		PartnerApplication application = applications.lockByPublicId(publicId).orElseThrow(ReviewService::notFound);
		action.accept(application);
		applications.saveAndFlush(application);
		return caseView(caller, publicId);
	}

	private void record(PartnerApplication application, Caller caller, String action, String decision, String reasonCode,
			String note, String target) {
		String targetRef = target == null ? null : target.length() > 40 ? target.substring(0, 40) : target;
		actions.save(new ReviewAction(application.getId(), caller.subjectId(), action, decision, reasonCode,
				note == null ? null : note.length() > 1000 ? note.substring(0, 1000) : note, targetRef, Instant.now(clock)));
		Map<String, String> details = new LinkedHashMap<>();
		if (decision != null) {
			details.put("decision", decision);
		}
		if (reasonCode != null) {
			details.put("reason_code", reasonCode);
		}
		if (target != null) {
			details.put("target", targetRef);
		}
		audit.record(new AuditEntry("REVIEW_" + action, "STAFF", caller.subjectId().toString(), "PARTNER_APPLICATION",
				application.getPublicId(), details));
	}

	private Optional<ReviewAction> latestRecommendation(PartnerApplication application) {
		List<ReviewAction> all = actions.findByApplicationIdOrderByOccurredAtAsc(application.getId());
		// A recommendation counts only if nothing was sent back after it
		for (int i = all.size() - 1; i >= 0; i--) {
			ReviewAction action = all.get(i);
			if (action.getAction().equals("REQUEST_CORRECTION")) {
				return Optional.empty();
			}
			if (action.getAction().equals(ReviewAction.RECOMMEND)) {
				return Optional.of(action);
			}
		}
		return Optional.empty();
	}

	private static void requireAssigned(PartnerApplication application, Caller caller) {
		if (!caller.subjectId().equals(application.getAssignedStaffId())) {
			throw new ForbiddenException("NOT_ASSIGNED", "Claim the case first");
		}
	}

	private static void requireReviewing(PartnerApplication application, Caller caller) {
		if (application.getStatus() != ApplicationStatus.UNDER_REVIEW) {
			throw new ApiProblem(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION", "The case is not under review",
					Map.of("current_status", application.getStatus().name()));
		}
		requireAssigned(application, caller);
	}

	private static void requireRole(Caller caller, Role role) {
		if (!caller.hasRole(role)) {
			throw new ForbiddenException("FORBIDDEN", "Your role cannot do this");
		}
	}

	private static void requireAnyRole(Caller caller) {
		if (!caller.hasRole(Role.REVIEWER) && !caller.hasRole(Role.APPROVER)) {
			throw new ForbiddenException("FORBIDDEN", "Your role cannot do this");
		}
	}

	private static String reason(boolean verified, String reasonCode) {
		if (!verified && (reasonCode == null || reasonCode.isBlank())) {
			throw ValidationException.field("reason_code", "Say why it is rejected");
		}
		return verified ? null : code(reasonCode);
	}

	/** Reason codes are short machine codes, e.g. {@code BLURRY}; free text goes in the note. */
	private static String code(String reasonCode) {
		String value = reasonCode == null ? "" : reasonCode.trim().toUpperCase();
		if (!value.matches("[A-Z][A-Z0-9_]{1,39}")) {
			throw ValidationException.field("reason_code", "Use a short code such as BLURRY or NAME_MISMATCH");
		}
		return value;
	}

	private String staffName(UUID staffId) {
		return staffId == null ? null : staff.find(staffId).map(StaffView::displayName).orElse("Unknown");
	}

	private PartnerApplication find(String publicId) {
		return applications.findByPublicId(publicId).orElseThrow(ReviewService::notFound);
	}

	private static NotFoundException notFound() {
		return new NotFoundException("APPLICATION_NOT_FOUND", "Application not found");
	}

	private static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
		try {
			return Enum.valueOf(type, value.trim().toUpperCase());
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			throw ValidationException.field(field, "Unknown value " + value);
		}
	}
}
