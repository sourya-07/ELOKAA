package in.eko.eloka.partner.onboarding.internal;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.eko.eloka.partner.documents.DocumentContent;
import in.eko.eloka.partner.shared.error.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** The identity and document steps of an application. */
@RestController
@RequestMapping("/v1/partner/applications/{id}")
class StepController {

	private final IdentitySteps identity;
	private final DocumentSteps documents;
	private final EligibilityStep eligibility;
	private final Idempotency idempotency;

	StepController(IdentitySteps identity, DocumentSteps documents, EligibilityStep eligibility, Idempotency idempotency) {
		this.identity = identity;
		this.documents = documents;
		this.eligibility = eligibility;
		this.idempotency = idempotency;
	}

	record PanRequest(String pan, String name, String dob) {
	}

	record DigiLockerComplete(String state) {
	}

	record IdDocumentRequest(@JsonProperty("voter_id_number") String voterIdNumber,
			@JsonProperty("driving_licence_number") String drivingLicenceNumber) {
	}

	@PostMapping(path = "/aadhaar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<ApplicationSnapshot> aadhaar(@PathVariable String id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
			@RequestParam(value = "aadhaar_last4", required = false) String last4,
			@RequestParam(value = "front", required = false) MultipartFile front,
			@RequestParam(value = "back", required = false) MultipartFile back) {
		long version = WebSupport.expectedVersion(ifMatch);
		return WebSupport.ok(identity.manualAadhaar(WebSupport.applicantId(), id, version, last4, name(front),
				bytes(front), name(back), bytes(back)));
	}

	@PostMapping("/digilocker-sessions")
	Map<String, String> startDigiLocker(@PathVariable String id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
		long version = WebSupport.expectedVersion(ifMatch);
		return Map.of("url", identity.startDigiLocker(WebSupport.applicantId(), id, version));
	}

	/** 202 while DigiLocker has no answer yet (the client asks again), else 200 with the application. */
	@PostMapping("/digilocker-sessions/complete")
	ResponseEntity<ApplicationSnapshot> completeDigiLocker(@PathVariable String id, @RequestBody DigiLockerComplete body) {
		IdentitySteps.DigiLockerResult result = identity.completeDigiLocker(WebSupport.applicantId(), id,
				body == null ? null : body.state());
		return ResponseEntity.status(result.pending() ? HttpStatus.ACCEPTED : HttpStatus.OK)
			.eTag(WebSupport.etag(result.application()))
			.body(result.application());
	}

	/** A billable provider call: needs {@code If-Match} and {@code Idempotency-Key}. */
	@PostMapping("/pan-verifications")
	ResponseEntity<?> pan(@PathVariable String id, @RequestBody PanRequest body,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
			@RequestHeader(value = Idempotency.HEADER, required = false) String key, HttpServletRequest request) {
		long version = WebSupport.expectedVersion(ifMatch);
		UUID applicantId = WebSupport.applicantId();
		LocalDate dob = parseDob(body == null ? null : body.dob());
		return idempotency.run(applicantId, key, WebSupport.fingerprint(request, version + " " + body),
				() -> WebSupport.ok(identity.verifyPan(applicantId, id, version, body.pan(), body.name(), dob)));
	}

	/** A credit bureau enquiry: needs {@code If-Match} and {@code Idempotency-Key}. */
	@PostMapping("/credit-assessments")
	ResponseEntity<?> creditCheck(@PathVariable String id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
			@RequestHeader(value = Idempotency.HEADER, required = false) String key, HttpServletRequest request) {
		long version = WebSupport.expectedVersion(ifMatch);
		UUID applicantId = WebSupport.applicantId();
		return idempotency.run(applicantId, key, WebSupport.fingerprint(request, version),
				() -> WebSupport.ok(eligibility.check(applicantId, id, version)));
	}

	@PutMapping("/home-address")
	ResponseEntity<ApplicationSnapshot> homeAddress(@PathVariable String id, @RequestBody IdentitySteps.AddressInput body,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
		long version = WebSupport.expectedVersion(ifMatch);
		return WebSupport.ok(identity.saveHomeAddress(WebSupport.applicantId(), id, version, body));
	}

	@PostMapping(path = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<DocumentSteps.DocumentView> upload(@PathVariable String id,
			@RequestParam(value = "kind", required = false) String kind,
			@RequestParam(value = "file", required = false) MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw ValidationException.field("file", "Choose a file to upload");
		}
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(documents.upload(WebSupport.applicantId(), id, kind, file.getOriginalFilename(), bytes(file)));
	}

	@GetMapping("/documents")
	List<DocumentSteps.DocumentView> list(@PathVariable String id) {
		return documents.list(WebSupport.applicantId(), id);
	}

	/** The applicant's own file, never cached, never shown inline from another origin. */
	@GetMapping("/documents/{documentId}/content")
	ResponseEntity<byte[]> content(@PathVariable String id, @PathVariable UUID documentId) {
		DocumentContent content = documents.content(WebSupport.applicantId(), id, documentId);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(content.ref().contentType()))
			.cacheControl(CacheControl.noStore())
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.inline().filename(content.ref().fileName()).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.body(content.bytes());
	}

	@DeleteMapping("/documents/{documentId}")
	ResponseEntity<Void> remove(@PathVariable String id, @PathVariable UUID documentId) {
		documents.remove(WebSupport.applicantId(), id, documentId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/photos/submit")
	ResponseEntity<ApplicationSnapshot> submitPhotos(@PathVariable String id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
		return WebSupport.ok(documents.submitPhotos(WebSupport.applicantId(), id, WebSupport.expectedVersion(ifMatch)));
	}

	@PostMapping("/education/submit")
	ResponseEntity<ApplicationSnapshot> submitEducation(@PathVariable String id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
		return WebSupport.ok(documents.submitEducation(WebSupport.applicantId(), id, WebSupport.expectedVersion(ifMatch)));
	}

	@PostMapping("/id-document/submit")
	ResponseEntity<ApplicationSnapshot> submitIdDocument(@PathVariable String id, @RequestBody IdDocumentRequest body,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
		return WebSupport.ok(documents.submitIdDocument(WebSupport.applicantId(), id, WebSupport.expectedVersion(ifMatch),
				body == null ? null : body.voterIdNumber(), body == null ? null : body.drivingLicenceNumber()));
	}

	private static LocalDate parseDob(String dob) {
		if (dob == null || dob.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(dob.trim());
		}
		catch (DateTimeParseException ex) {
			throw ValidationException.field("dob", "Use the format YYYY-MM-DD");
		}
	}

	private static String name(MultipartFile file) {
		return file == null ? null : file.getOriginalFilename();
	}

	private static byte[] bytes(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			return null;
		}
		try {
			return file.getBytes();
		}
		catch (IOException ex) {
			throw ValidationException.field("file", "The upload did not arrive completely. Please try again.");
		}
	}
}
