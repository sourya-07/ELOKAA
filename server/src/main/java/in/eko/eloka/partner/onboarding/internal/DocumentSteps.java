package in.eko.eloka.partner.onboarding.internal;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
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
	private static final int MAX_COMPUTER_CERTIFICATES = 5;
	private static final java.util.regex.Pattern EPIC = java.util.regex.Pattern.compile("[A-Z]{3}\\d{7}");
	private static final java.util.regex.Pattern DL = java.util.regex.Pattern.compile("[A-Z]{2}\\d{13}");

	record DocumentView(@JsonProperty("document_id") String documentId, String kind, String status,
			@JsonProperty("file_name") String fileName, @JsonProperty("content_type") String contentType, long size,
			@JsonProperty("reject_reason") String rejectReason, @JsonProperty("uploaded_at") Instant uploadedAt) {
	}

	private final ApplicationService applications;
	private final ApplicationDocumentRepository links;
	private final IdNumberRepository idNumbers;
	private final DocumentApi documents;
	private final FieldCipher cipher;
	private final JsonMapper json;

	DocumentSteps(ApplicationService applications, ApplicationDocumentRepository links, IdNumberRepository idNumbers,
			DocumentApi documents, FieldCipher cipher, JsonMapper json) {
		this.applications = applications;
		this.links = links;
		this.idNumbers = idNumbers;
		this.documents = documents;
		this.cipher = cipher;
		this.json = json;
	}

	static PartnerStep stepOf(DocumentKind kind) {
		return switch (kind) {
			case LIVE_PHOTO, PASSPORT_PHOTO -> PartnerStep.PHOTOS;
			case GRADUATION_CERTIFICATE, COMPUTER_CERTIFICATE -> PartnerStep.EDUCATION;
			case VOTER_ID_FRONT, VOTER_ID_BACK, DRIVING_LICENCE_FRONT, DRIVING_LICENCE_BACK -> PartnerStep.ID_DOCUMENT;
			case AADHAAR_MASKED_FRONT, AADHAAR_MASKED_BACK -> PartnerStep.AADHAAR;
		};
	}

	/**
	 * Uploads may run in parallel (two files at once), so they take the row lock without an
	 * {@code If-Match}; each changes the version, which the client then reads.
	 */
	@Transactional
	DocumentView upload(UUID applicantId, String publicId, String kindName, String fileName, byte[] bytes) {
		DocumentKind kind = parseKind(kindName);
		if (stepOf(kind) == PartnerStep.AADHAAR) {
			throw ValidationException.field("kind", "Aadhaar images are sent with the Aadhaar step");
		}
		return applications.write(applicantId, publicId, null, context -> attach(context, applicantId, kind, fileName, bytes));
	}

	/** Stores a file and links it to the application under the caller's lock. */
	DocumentView attach(StepContext context, UUID applicantId, DocumentKind kind, String fileName, byte[] bytes) {
		PartnerStep step = stepOf(kind);
		context.requirePrerequisites(step);
		requireEditable(context, step);
		List<ApplicationDocument> active = links.findByApplicationIdAndRemovedAtIsNullOrderByCreatedAtAsc(context.applicationId());
		if (kind == DocumentKind.COMPUTER_CERTIFICATE
				&& active.stream().filter(d -> d.getKind() == kind).count() >= MAX_COMPUTER_CERTIFICATES) {
			throw new ApiProblem(HttpStatus.CONFLICT, "TOO_MANY_DOCUMENTS",
					"You can add up to " + MAX_COMPUTER_CERTIFICATES + " computer certificates");
		}
		DocumentRef stored = documents.store(new NewDocument(applicantId, kind, fileName, bytes));
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
			requireAll(have, DocumentKind.LIVE_PHOTO, DocumentKind.PASSPORT_PHOTO);
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
			requireAll(have, DocumentKind.GRADUATION_CERTIFICATE);
			context.complete(PartnerStep.EDUCATION, json.writeValueAsString(Map.of("graduation_certificate", true,
					"computer_certificates", have.getOrDefault(DocumentKind.COMPUTER_CERTIFICATE, 0L))));
			return null;
		});
		return applications.get(applicantId, publicId);
	}

	/**
	 * At least one of voter ID or driving licence, each with its number and both sides. A number
	 * without its images (or images without the number) is refused, so nothing half-done is kept.
	 */
	@Transactional
	ApplicationSnapshot submitIdDocument(UUID applicantId, String publicId, long version, String voterNumber,
			String licenceNumber) {
		applications.write(applicantId, publicId, version, context -> {
			context.requirePrerequisites(PartnerStep.ID_DOCUMENT);
			Map<DocumentKind, Long> have = counts(context);
			String voter = normalise(voterNumber);
			String licence = normalise(licenceNumber);
			boolean voterImages = have.containsKey(DocumentKind.VOTER_ID_FRONT) || have.containsKey(DocumentKind.VOTER_ID_BACK);
			boolean licenceImages = have.containsKey(DocumentKind.DRIVING_LICENCE_FRONT)
					|| have.containsKey(DocumentKind.DRIVING_LICENCE_BACK);
			if (voter == null && licence == null) {
				throw ValidationException.field("voter_id_number", "Add your voter ID or your driving licence");
			}
			Map<String, Object> summary = new LinkedHashMap<>();
			if (voter != null || voterImages) {
				if (voter == null || !EPIC.matcher(voter).matches()) {
					throw ValidationException.field("voter_id_number", "Enter the 10-character voter ID (EPIC) number");
				}
				requireAll(have, DocumentKind.VOTER_ID_FRONT, DocumentKind.VOTER_ID_BACK);
				summary.put("voter_id", save(context, IdNumber.Type.VOTER_ID, voter));
			}
			if (licence != null || licenceImages) {
				if (licence == null || !DL.matcher(licence).matches()) {
					throw ValidationException.field("driving_licence_number",
							"Enter the driving licence number, for example MH0120110012345");
				}
				requireAll(have, DocumentKind.DRIVING_LICENCE_FRONT, DocumentKind.DRIVING_LICENCE_BACK);
				summary.put("driving_licence", save(context, IdNumber.Type.DRIVING_LICENCE, licence));
			}
			context.complete(PartnerStep.ID_DOCUMENT, json.writeValueAsString(summary));
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

	private String save(StepContext context, IdNumber.Type type, String number) {
		IdNumber row = idNumbers.findByApplicationIdAndType(context.applicationId(), type)
			.orElseGet(() -> new IdNumber(context.applicationId(), type));
		String masked = "*".repeat(number.length() - 4) + number.substring(number.length() - 4);
		row.set(cipher.encrypt(number), masked);
		idNumbers.save(row);
		return masked;
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

	private static String normalise(String number) {
		if (number == null || number.isBlank()) {
			return null;
		}
		return number.toUpperCase().replaceAll("[\\s-]", "");
	}

	private static DocumentKind parseKind(String name) {
		try {
			return DocumentKind.valueOf(name == null ? "" : name.trim().toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			throw ValidationException.field("kind", "Unknown document kind");
		}
	}

	private static DocumentView view(ApplicationDocument link, DocumentRef ref) {
		return new DocumentView(link.getDocumentId().toString(), link.getKind().name(), link.getStatus().name(),
				ref.fileName(), ref.contentType(), ref.size(), link.getRejectReason(), ref.uploadedAt());
	}

	private static NotFoundException notFound() {
		return new NotFoundException("DOCUMENT_NOT_FOUND", "Document not found");
	}
}
