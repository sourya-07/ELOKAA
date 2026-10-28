package in.eko.eloka.partner.onboarding.internal;

import java.io.IOException;
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
	private final ContactSteps contact;

	StepController(IdentitySteps identity, DocumentSteps documents, EligibilityStep eligibility, Idempotency idempotency,
			ContactSteps contact) {
		this.contact = contact;
		this.identity = identity;
		this.documents = documents;
		this.eligibility = eligibility;
		this.idempotency = idempotency;
	}

	/** Only the PAN: the name and date of birth checked against it come from DigiLocker. */
	record PanRequest(String pan) {
	}

	record EmailRequest(String email) {
	}

	record EmailCode(@JsonProperty("challenge_ref") String challengeRef, String otp) {
	}

	/** Sends a code to the address; changes nothing on the application (no {@code If-Match}). */
	@PostMapping("/email-verifications")
	ResponseEntity<ContactSteps.EmailCodeSent> sendEmailCode(@PathVariable String id, @RequestBody EmailRequest body,
			HttpServletRequest request) {
		return ResponseEntity.accepted()
			.body(contact.sendEmailCode(WebSupport.applicantId(), id, body == null ? null : body.email(),
					request.getRemoteAddr()));
	}

	/** A one-time code: needs {@code If-Match} and {@code Idempotency-Key}, so a lost answer can be replayed. */
	@PostMapping("/email-verifications/verify")
	ResponseEntity<?> verifyEmail(@PathVariable String id, @RequestBody EmailCode body,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
			@RequestHeader(value = Idempotency.HEADER, required = false) String key, HttpServletRequest request) {
		long version = WebSupport.expectedVersion(ifMatch);
		UUID applicantId = WebSupport.applicantId();
		return idempotency.run(applicantId, key, WebSupport.fingerprint(request, version + " " + body),
				() -> WebSupport.ok(contact.verifyEmail(applicantId, id, version, body == null ? null : body.challengeRef(),
						body == null ? null : body.otp())));
	}

	record DigiLockerComplete(String state) {
	}

	record AlternateIdRequest(String type, String number) {
	}

	/** A billable record check: needs {@code If-Match} and {@code Idempotency-Key}. */
	@PostMapping("/alternate-id-verifications")
	ResponseEntity<?> alternateId(@PathVariable String id, @RequestBody AlternateIdRequest body,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
			@RequestHeader(value = Idempotency.HEADER, required = false) String key, HttpServletRequest request) {
		long version = WebSupport.expectedVersion(ifMatch);
		UUID applicantId = WebSupport.applicantId();
		return idempotency.run(applicantId, key, WebSupport.fingerprint(request, version + " " + body),
				() -> WebSupport.ok(identity.verifyAlternateId(applicantId, id, version, body == null ? null : body.type(),
						body == null ? null : body.number())));
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
		return idempotency.run(applicantId, key, WebSupport.fingerprint(request, version + " " + body),
				() -> WebSupport.ok(identity.verifyPan(applicantId, id, version, body == null ? null : body.pan())));
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
			@RequestParam(value = "file", required = false) MultipartFile file,
			@RequestParam(value = "latitude", required = false) String latitude,
			@RequestParam(value = "longitude", required = false) String longitude,
			@RequestParam(value = "accuracy_m", required = false) String accuracyM,
			@RequestParam(value = "captured_at", required = false) String capturedAt) {
		if (file == null || file.isEmpty()) {
			throw ValidationException.field("file", "Choose a file to upload");
		}
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(documents.upload(WebSupport.applicantId(), id, kind, file.getOriginalFilename(), bytes(file),
					new LocationRules.Input(latitude, longitude, accuracyM, capturedAt)));
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

	@PostMapping("/kyc-documents/submit")
	ResponseEntity<ApplicationSnapshot> submitKycDocuments(@PathVariable String id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
		return WebSupport.ok(documents.submitKycDocuments(WebSupport.applicantId(), id, WebSupport.expectedVersion(ifMatch)));
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
