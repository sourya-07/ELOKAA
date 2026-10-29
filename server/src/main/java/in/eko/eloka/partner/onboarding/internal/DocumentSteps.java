package in.eko.eloka.partner.onboarding.internal;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.eko.eloka.partner.audit.AuditEntry;
import in.eko.eloka.partner.audit.AuditLog;
import in.eko.eloka.partner.documents.DocumentApi;
import in.eko.eloka.partner.documents.DocumentContent;
import in.eko.eloka.partner.documents.DocumentKind;
import in.eko.eloka.partner.documents.DocumentRef;
import in.eko.eloka.partner.documents.NewDocument;
import in.eko.eloka.partner.onboarding.PartnerStep;
import in.eko.eloka.partner.shared.crypto.FieldCipher;
import in.eko.eloka.partner.shared.error.ApiProblem;
import in.eko.eloka.partner.shared.error.NotFoundException;
import in.eko.eloka.partner.shared.error.ValidationException;
import in.eko.eloka.partner.verification.VerificationApi;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Uploads for the photo, education and ID-document steps, and submitting those steps. An upload
 * never completes a step by itself: the applicant submits the step, and the server checks that
 * every required file is there. Every file waits for staff review.
 */
@Service
class DocumentSteps {

	/** Kinds that may be uploaded once; a new upload replaces the old one. */
	private static final Set<DocumentKind> SINGLE = EnumSet.complementOf(EnumSet.of(DocumentKind.COMPUTER_CERTIFICATE));
	static final int MAX_COMPUTER_CERTIFICATES = 5;

	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	record DocumentView(@JsonProperty("document_id") String documentId, String kind, String status,
			@JsonProperty("file_name") String fileName, @JsonProperty("content_type") String contentType, long size,
			@JsonProperty("reject_reason") String rejectReason, @JsonProperty("uploaded_at") Instant uploadedAt,
			Location location) {
	}

	/**
	 * The live photo's location as the applicant's browser reported it, shown back to that applicant only
	 * (their own files; read from the encrypted photo_location row, never kept in a step summary). It is
	 * where the device said it was, not a checked address.
	 */
	record Location(double latitude, double longitude, @JsonProperty("accuracy_m") long accuracyM,
			@JsonProperty("captured_at") Instant capturedAt, String flags) {
	}

	private final ApplicationService applications;
	private final ApplicationDocumentRepository links;
	private final DocumentApi documents;
	private final VerificationApi verification;
	private final LocationRules locationRules;
	private final PhotoLocationRepository locations;
	private final FieldCipher cipher;
	private final AuditLog audit;
	private final JsonMapper json;

	DocumentSteps(ApplicationService applications, ApplicationDocumentRepository links, DocumentApi documents,
			VerificationApi verification, LocationRules locationRules, PhotoLocationRepository locations, FieldCipher cipher,
			AuditLog audit, JsonMapper json) {
		this.applications = applications;
		this.links = links;
		this.documents = documents;
		this.verification = verification;
		this.locationRules = locationRules;
		this.locations = locations;
		this.cipher = cipher;
		this.audit = audit;
		this.json = json;
	}

	static PartnerStep stepOf(DocumentKind kind) {
		return switch (kind) {
			case LIVE_PHOTO, PASSPORT_PHOTO -> PartnerStep.PHOTOS;
			case CLASS_10_MARKSHEET, CLASS_12_MARKSHEET, GRADUATION_CERTIFICATE, DIPLOMA_CERTIFICATE, COMPUTER_CERTIFICATE ->
				PartnerStep.EDUCATION;
			case AADHAAR_FRONT, AADHAAR_BACK, PAN_CARD, VOTER_ID_FRONT, VOTER_ID_BACK, DRIVING_LICENCE_FRONT,
					DRIVING_LICENCE_BACK -> PartnerStep.KYC_DOCUMENTS;
			case AADHAAR_MASKED_FRONT, AADHAAR_MASKED_BACK -> PartnerStep.AADHAAR; // before journey v2 only
		};
	}

	/**
	 * Uploads may run in parallel (two files at once), so they take the row lock without an
	 * {@code If-Match}; each changes the version, which the client then reads.
	 */
	@Transactional
	DocumentView upload(UUID applicantId, String publicId, String kindName, String fileName, byte[] bytes,
			LocationRules.Input location) {
		DocumentKind kind = parseKind(kindName);
		if (stepOf(kind) == PartnerStep.AADHAAR) {
			throw ValidationException.field("kind", "Aadhaar images are sent with the Aadhaar step");
		}
		return applications.write(applicantId, publicId, null, context -> {
			// The live photo is refused without a real location, before anything is stored (never invented);
			// the earlier steps are checked first, so the answer says what to do first
			LocationRules.Checked where = null;
			if (kind == DocumentKind.LIVE_PHOTO) {
				context.requirePrerequisites(PartnerStep.PHOTOS);
				where = locationRules.check(location, context.now());
			}
			DocumentView view = attach(context, applicantId, kind, fileName, bytes);
			LocationRules.Checked found = where;
			if (found == null) {
				return view;
			}
			locations.save(new PhotoLocation(context.applicationId(), UUID.fromString(view.documentId()),
					cipher.encrypt(Double.toString(found.latitude())), cipher.encrypt(Double.toString(found.longitude())),
					found.accuracyM(), found.capturedAt(), context.now(), found.flags()));
			audit.record(new AuditEntry("LOCATION_RECORDED", "APPLICANT", applicantId.toString(), "PARTNER_APPLICATION",
					publicId, Map.of("flags", found.flags().isEmpty() ? "NONE" : found.flags())));
			return new DocumentView(view.documentId(), view.kind(), view.status(), view.fileName(), view.contentType(),
					view.size(), view.rejectReason(), view.uploadedAt(), new Location(found.latitude(), found.longitude(),
							Math.round(found.accuracyM()), found.capturedAt(), found.flags()));
		});
	}

	/** Stores a file and links it to the application under the caller's lock. */
	DocumentView attach(StepContext context, UUID applicantId, DocumentKind kind, String fileName, byte[] bytes) {
		PartnerStep step = stepOf(kind);
		requireEditable(context, step); // frozen during a correction: the real reason, before "earlier steps first"
		context.requirePrerequisites(step);
		List<ApplicationDocument> active = links.findByApplicationIdAndRemovedAtIsNullOrderByCreatedAtAsc(context.applicationId());
		if (kind == DocumentKind.COMPUTER_CERTIFICATE
				&& active.stream().filter(d -> d.getKind() == kind).count() >= MAX_COMPUTER_CERTIFICATES) {
			throw new ApiProblem(HttpStatus.CONFLICT, "TOO_MANY_DOCUMENTS",
					"You can add up to " + MAX_COMPUTER_CERTIFICATES + " computer certificates");
		}
		DocumentRef stored = documents.store(new NewDocument(applicantId, kind, fileName, bytes));
		// The same picture in two places (Aadhaar front as the back, one scan for two marksheets) is a mistake
		boolean duplicate = active.stream()
			.filter(d -> d.getKind() != kind || !SINGLE.contains(kind))
			.anyMatch(d -> java.security.MessageDigest.isEqual(documents.describe(d.getDocumentId()).sha256(), stored.sha256()));
		if (duplicate) {
			throw new ApiProblem(HttpStatus.UNPROCESSABLE_CONTENT, "DUPLICATE_FILE",
					"You already uploaded this same file for another document. Choose the right file.");
		}
		if (SINGLE.contains(kind)) {
			active.stream().filter(d -> d.getKind() == kind).forEach(d -> d.remove(context.now()));
		}
		ApplicationDocument link = links.save(new ApplicationDocument(context.applicationId(), stored.id(), kind));
		context.changed(step);
		context.record("DOCUMENT_UPLOADED", step);
		return view(link, stored);
	}

	@Transactional
	void remove(UUID applicantId, String publicId, UUID documentId) {
		applications.write(applicantId, publicId, null, context -> {
			ApplicationDocument link = links.findByDocumentIdAndApplicationId(documentId, context.applicationId())
				.filter(ApplicationDocument::isActive)
				.orElseThrow(DocumentSteps::notFound);
			PartnerStep step = stepOf(link.getKind());
			if (step == PartnerStep.AADHAAR) {
				throw new ApiProblem(HttpStatus.CONFLICT, "DOCUMENT_REQUIRED", "Upload a new Aadhaar image instead");
			}
			requireEditable(context, step);
			link.remove(context.now());
			context.changed(step);
			context.record("DOCUMENT_REMOVED", step);
			return null;
		});
	}

	@Transactional(readOnly = true)
	List<DocumentView> list(UUID applicantId, String publicId) {
		PartnerApplication application = applications.find(applicantId, publicId);
		return links.findByApplicationIdAndRemovedAtIsNullOrderByCreatedAtAsc(application.getId())
			.stream()
			.map(link -> view(link, documents.describe(link.getDocumentId())))
			.toList();
	}

	@Transactional(readOnly = true)
	DocumentContent content(UUID applicantId, String publicId, UUID documentId) {
		PartnerApplication application = applications.find(applicantId, publicId);
		// A replaced or removed file is no longer the applicant's to fetch; staff keep access for review history
		links.findByDocumentIdAndApplicationId(documentId, application.getId())
			.filter(ApplicationDocument::isActive)
			.orElseThrow(DocumentSteps::notFound);
		return documents.load(applicantId, documentId);
	}

	@Transactional
	ApplicationSnapshot submitPhotos(UUID applicantId, String publicId, long version) {
		applications.write(applicantId, publicId, version, context -> {
			context.requirePrerequisites(PartnerStep.PHOTOS);
			Map<DocumentKind, Long> have = counts(context);
			DocumentRequirements.requireMet(PartnerStep.PHOTOS, have);
			context.complete(PartnerStep.PHOTOS, json.writeValueAsString(Map.of("live_photo", true, "passport_photo", true)));
			return null;
		});
		return applications.get(applicantId, publicId);
	}

	@Transactional
	ApplicationSnapshot submitEducation(UUID applicantId, String publicId, long version) {
		applications.write(applicantId, publicId, version, context -> {
			context.requirePrerequisites(PartnerStep.EDUCATION);
			Map<DocumentKind, Long> have = counts(context);
			DocumentRequirements.requireMet(PartnerStep.EDUCATION, have);
			context.complete(PartnerStep.EDUCATION, json.writeValueAsString(Map.of(
					"degree", have.containsKey(DocumentKind.GRADUATION_CERTIFICATE) ? "GRADUATION" : "DIPLOMA",
					"computer_certificates", have.getOrDefault(DocumentKind.COMPUTER_CERTIFICATE, 0L))));
			return null;
		});
		return applications.get(applicantId, publicId);
	}

	/**
	 * The identity documents: Aadhaar front and back (full images, ADR-0008), the PAN card, and both
	 * sides of the voter ID or driving licence that was verified (of either, when both were). The
	 * numbers were checked in the identity steps; they are not asked again.
	 */
	@Transactional
	ApplicationSnapshot submitKycDocuments(UUID applicantId, String publicId, long version) {
		applications.write(applicantId, publicId, version, context -> {
			context.requirePrerequisites(PartnerStep.KYC_DOCUMENTS);
			Map<DocumentKind, Long> have = counts(context);
			List<String> missing = new java.util.ArrayList<>();
			for (DocumentKind kind : List.of(DocumentKind.AADHAAR_FRONT, DocumentKind.AADHAAR_BACK, DocumentKind.PAN_CARD)) {
				if (!have.containsKey(kind)) {
					missing.add(kind.name());
				}
			}
			String idDocument = null;
			List<DocumentKind> idGap = null;
			for (VerificationApi.AlternateType type : verification.verifiedAlternateIds(context.applicationId())) {
				List<DocumentKind> sides = type == VerificationApi.AlternateType.VOTER_ID
						? List.of(DocumentKind.VOTER_ID_FRONT, DocumentKind.VOTER_ID_BACK)
						: List.of(DocumentKind.DRIVING_LICENCE_FRONT, DocumentKind.DRIVING_LICENCE_BACK);
				List<DocumentKind> gap = sides.stream().filter(kind -> !have.containsKey(kind)).toList();
				if (gap.isEmpty()) {
					idDocument = type.name();
				}
				else if (idGap == null || gap.size() < idGap.size()) {
					idGap = gap;
				}
			}
			if (idDocument == null && idGap != null) {
				idGap.forEach(kind -> missing.add(kind.name()));
			}
			if (!missing.isEmpty() || idDocument == null) {
				throw new ApiProblem(HttpStatus.CONFLICT, "DOCUMENTS_MISSING", "Some required files are missing",
						Map.of("missing_documents", missing));
			}
			context.complete(PartnerStep.KYC_DOCUMENTS,
					json.writeValueAsString(Map.of("aadhaar", true, "pan_card", true, "id_document", idDocument)));
			return null;
		});
		return applications.get(applicantId, publicId);
	}

	/** During a correction only the reopened steps can change; the rest stay frozen. */
	private static void requireEditable(StepContext context, PartnerStep step) {
		if (context.step(step).getStatus() == in.eko.eloka.partner.onboarding.StepStatus.LOCKED) {
			throw new ApiProblem(HttpStatus.CONFLICT, "STEP_NOT_EDITABLE", "This step cannot be changed now");
		}
	}

	private Map<DocumentKind, Long> counts(StepContext context) {
		Map<DocumentKind, Long> counts = new java.util.EnumMap<>(DocumentKind.class);
		links.findByApplicationIdAndRemovedAtIsNullOrderByCreatedAtAsc(context.applicationId())
			.forEach(link -> counts.merge(link.getKind(), 1L, Long::sum));
		return counts;
	}

	private static void requireAll(Map<DocumentKind, Long> have, DocumentKind... kinds) {
		List<String> missing = java.util.Arrays.stream(kinds).filter(k -> !have.containsKey(k)).map(Enum::name).toList();
		if (!missing.isEmpty()) {
			throw new ApiProblem(HttpStatus.CONFLICT, "DOCUMENTS_MISSING", "Some required files are missing",
					Map.of("missing_documents", missing));
		}
	}

	private static DocumentKind parseKind(String name) {
		try {
			return DocumentKind.valueOf(name == null ? "" : name.trim().toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			throw ValidationException.field("kind", "Unknown document kind");
		}
	}

	private DocumentView view(ApplicationDocument link, DocumentRef ref) {
		Location location = link.getKind() != DocumentKind.LIVE_PHOTO ? null
				: locations.findByDocumentId(link.getDocumentId())
					.map(l -> new Location(Double.parseDouble(cipher.decrypt(l.getLatitudeEnc())),
							Double.parseDouble(cipher.decrypt(l.getLongitudeEnc())), Math.round(l.getAccuracyM()), l.getCapturedAt(),
							l.getFlags()))
					.orElse(null);
		return new DocumentView(link.getDocumentId().toString(), link.getKind().name(), link.getStatus().name(),
				ref.fileName(), ref.contentType(), ref.size(), link.getRejectReason(), ref.uploadedAt(), location);
	}

	private static NotFoundException notFound() {
		return new NotFoundException("DOCUMENT_NOT_FOUND", "Document not found");
	}
}
