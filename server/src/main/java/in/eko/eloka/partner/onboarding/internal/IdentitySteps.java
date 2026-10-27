package in.eko.eloka.partner.onboarding.internal;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import in.eko.eloka.partner.audit.AuditEntry;
import in.eko.eloka.partner.audit.AuditLog;
import in.eko.eloka.partner.documents.DocumentKind;
import in.eko.eloka.partner.onboarding.PartnerStep;
import in.eko.eloka.partner.onboarding.StepStatus;
import in.eko.eloka.partner.onboarding.VerificationMethod;
import in.eko.eloka.partner.shared.crypto.FieldCipher;
import in.eko.eloka.partner.shared.error.ApiProblem;
import in.eko.eloka.partner.shared.error.ValidationException;
import in.eko.eloka.partner.verification.VerificationApi;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * The identity steps: Aadhaar (DigiLocker or masked images), PAN, home address. Each step is
 * completed only after its check: the provider's answer, or the evidence that staff will review.
 * Provider calls run under the application's row lock, so one applicant cannot run two at once.
 */
@Service
class IdentitySteps {

	private static final java.util.regex.Pattern PINCODE = java.util.regex.Pattern.compile("[1-9]\\d{5}");

	/** What the client shows after the applicant comes back from DigiLocker. */
	record DigiLockerResult(boolean pending, ApplicationSnapshot application) {
	}

	record AddressInput(String line1, String line2, String city, String district, String state, String pincode) {
	}

	private final ApplicationService applications;
	private final DocumentSteps documents;
	private final VerificationApi verification;
	private final ConsentRecordRepository consents;
	private final PartnerAddressRepository addresses;
	private final PartnerApplicationRepository applicationRows;
	private final OnboardingProperties properties;
	private final FieldCipher cipher;
	private final AuditLog audit;
	private final JsonMapper json;

	IdentitySteps(ApplicationService applications, DocumentSteps documents, VerificationApi verification,
			ConsentRecordRepository consents, PartnerAddressRepository addresses,
			PartnerApplicationRepository applicationRows, OnboardingProperties properties, FieldCipher cipher,
			AuditLog audit, JsonMapper json) {
		this.applications = applications;
		this.documents = documents;
		this.verification = verification;
		this.consents = consents;
		this.addresses = addresses;
		this.applicationRows = applicationRows;
		this.properties = properties;
		this.cipher = cipher;
		this.audit = audit;
		this.json = json;
	}

	/** Step by step: masked Aadhaar front and back plus the last 4 digits. Staff check them later. */
	@Transactional
	ApplicationSnapshot manualAadhaar(UUID applicantId, String publicId, long version, String last4, String frontName,
			byte[] front, String backName, byte[] back) {
		applications.write(applicantId, publicId, version, context -> {
			requireMethod(context, VerificationMethod.MANUAL);
			context.requirePrerequisites(PartnerStep.AADHAAR);
			if (front == null || back == null) {
				throw ValidationException.field("front", "Upload both sides of your Aadhaar card");
			}
			verification.recordManualAadhaar(context.applicationId(), last4);
			documents.attach(context, applicantId, DocumentKind.AADHAAR_MASKED_FRONT, frontName, front);
			documents.attach(context, applicantId, DocumentKind.AADHAAR_MASKED_BACK, backName, back);
			context.complete(PartnerStep.AADHAAR, json.writeValueAsString(Map.of("method", "MANUAL", "aadhaar_masked",
					"XXXX-XXXX-" + last4.trim(), "review", "MANUAL_REVIEW_REQUIRED")));
			return null;
		});
		return applications.get(applicantId, publicId);
	}

	/** Sends the applicant to DigiLocker. Needs the DigiLocker consent first. */
	@Transactional
	String startDigiLocker(UUID applicantId, String publicId, long version) {
		return applications.write(applicantId, publicId, version, context -> {
			requireMethod(context, VerificationMethod.DIGILOCKER);
			context.requirePrerequisites(PartnerStep.AADHAAR);
			boolean consented = consents
				.findFirstByApplicationIdAndPurposeOrderByAcceptedAtDesc(context.applicationId(), ConsentRecord.Purpose.DIGILOCKER)
				.filter(consent -> consent.getTextVersion().equals(properties.consentVersion()))
				.isPresent();
			if (!consented) {
				throw new ApiProblem(HttpStatus.CONFLICT, "CONSENT_REQUIRED", "Allow ELOKA to fetch your details from DigiLocker first",
						Map.of("purpose", "DIGILOCKER"));
			}
			String url = verification.startDigiLocker(context.applicationId(), properties.digilockerReturnUrl()).url();
			context.awaitExternal(PartnerStep.AADHAAR);
			return url;
		});
	}

	/**
	 * The applicant is back from DigiLocker. Runs without {@code If-Match}: the state token proves
	 * the call belongs to this trip, and it can be used once.
	 */
	@Transactional
	DigiLockerResult completeDigiLocker(UUID applicantId, String publicId, String state) {
		boolean pending = applications.write(applicantId, publicId, null, context -> {
			requireMethod(context, VerificationMethod.DIGILOCKER);
			VerificationApi.DigiLockerOutcome outcome = verification.completeDigiLocker(context.applicationId(), state);
			if (!(outcome instanceof VerificationApi.DigiLockerOutcome.Pending)) {
				audit.record(new AuditEntry("DIGILOCKER_RESULT", "APPLICANT", applicantId.toString(), "PARTNER_APPLICATION",
						publicId, Map.of("result", outcome instanceof VerificationApi.DigiLockerOutcome.Verified ? "VERIFIED"
								: ((VerificationApi.DigiLockerOutcome.Failed) outcome).code())));
			}
			switch (outcome) {
				case VerificationApi.DigiLockerOutcome.Pending p -> {
					return true;
				}
				case VerificationApi.DigiLockerOutcome.Failed failed -> context.fail(PartnerStep.AADHAAR, "DIGILOCKER_" + failed.code());
				case VerificationApi.DigiLockerOutcome.Verified verified -> {
					boolean addressComplete = saveDigiLockerAddress(context, verified.address());
					Map<String, Object> summary = new LinkedHashMap<>();
					summary.put("method", "DIGILOCKER");
					summary.put("aadhaar_masked", "XXXX-XXXX-" + verified.aadhaarLast4());
					summary.put("address_from_digilocker", addressComplete);
					context.complete(PartnerStep.AADHAAR, json.writeValueAsString(summary));
				}
			}
			return false;
		});
		return new DigiLockerResult(pending, applications.get(applicantId, publicId));
	}

	/**
	 * Checks the PAN. On the DigiLocker path the name and date of birth come from DigiLocker, not
	 * from what was typed. A PAN held by another open, approved or ineligible application is refused.
	 */
	@Transactional
	ApplicationSnapshot verifyPan(UUID applicantId, String publicId, long version, String pan, String name, LocalDate dob) {
		applications.write(applicantId, publicId, version, context -> {
			context.requirePrerequisites(PartnerStep.PAN);
			if (context.step(PartnerStep.PAN).getStatus() == StepStatus.BLOCKED) {
				throw new ApiProblem(HttpStatus.LOCKED, "STEP_BLOCKED", "Too many failed attempts on this step. Please contact support.");
			}
			VerificationApi.PanOutcome outcome = verification.verifyPan(context.applicationId(), pan, name, dob);
			String result = outcome.result() != VerificationApi.PanOutcome.Result.MATCH ? "PAN_" + outcome.result().name()
					: applicationRows.existsBlockingPan(outcome.panHash(), context.applicationId(), applications.blockingStatuses())
							? "PAN_ALREADY_USED" : "PAN_VERIFIED";
			audit.record(new AuditEntry("PAN_CHECKED", "APPLICANT", applicantId.toString(), "PARTNER_APPLICATION", publicId,
					Map.of("result", result)));
			if (!result.equals("PAN_VERIFIED")) {
				context.fail(PartnerStep.PAN, result);
				return null;
			}
			context.application().bindPan(outcome.panHash());
			context.complete(PartnerStep.PAN, json.writeValueAsString(Map.of("pan_masked", outcome.panMasked())));
			return null;
		});
		return applications.get(applicantId, publicId);
	}

	/** Home address typed by the applicant (or a correction of the DigiLocker one). */
	@Transactional
	ApplicationSnapshot saveHomeAddress(UUID applicantId, String publicId, long version, AddressInput input) {
		applications.write(applicantId, publicId, version, context -> {
			context.requirePrerequisites(PartnerStep.HOME_ADDRESS);
			AddressInput clean = validate(input);
			PartnerAddress address = addresses.findByApplicationId(context.applicationId())
				.orElseGet(() -> new PartnerAddress(context.applicationId()));
			address.set(cipher.encrypt(clean.line1()), clean.line2() == null ? null : cipher.encrypt(clean.line2()),
					cipher.encrypt(clean.city()), clean.district(), clean.state(), clean.pincode(),
					PartnerAddress.Source.SELF_DECLARED);
			addresses.save(address);
			context.complete(PartnerStep.HOME_ADDRESS, addressSummary(address));
			return null;
		});
		return applications.get(applicantId, publicId);
	}

	/**
	 * Completes the home address step from DigiLocker once the credit check has passed, when
	 * DigiLocker gave a whole address. Called by the eligibility step under the same lock.
	 */
	void completeAddressFromDigiLocker(StepContext context) {
		if (context.application().getMethod() != VerificationMethod.DIGILOCKER) {
			return;
		}
		if (context.step(PartnerStep.HOME_ADDRESS).getStatus().isSatisfied()) {
			return; // already done, or frozen during a correction
		}
		addresses.findByApplicationId(context.applicationId())
			.filter(address -> address.getSource() == PartnerAddress.Source.DIGILOCKER)
			.ifPresent(address -> context.complete(PartnerStep.HOME_ADDRESS, addressSummary(address)));
	}

	private boolean saveDigiLockerAddress(StepContext context, VerificationApi.Address from) {
		String line1 = join(from.line1(), from.line2());
		if (line1 == null || from.city() == null || from.state() == null || from.pincode() == null
				|| !PINCODE.matcher(from.pincode()).matches()) {
			return false; // the applicant types it on the address page
		}
		PartnerAddress address = addresses.findByApplicationId(context.applicationId())
			.orElseGet(() -> new PartnerAddress(context.applicationId()));
		address.set(cipher.encrypt(line1), null, cipher.encrypt(from.city()), from.district(), from.state(),
				from.pincode(), PartnerAddress.Source.DIGILOCKER);
		addresses.save(address);
		return true;
	}

	private String addressSummary(PartnerAddress address) {
		return json.writeValueAsString(Map.of("state", address.getState(), "pincode", address.getPincode(), "source",
				address.getSource().name()));
	}

	private static void requireMethod(StepContext context, VerificationMethod method) {
		if (context.application().getMethod() != method) {
			throw new ApiProblem(HttpStatus.CONFLICT, "WRONG_METHOD",
					"This application uses " + (method == VerificationMethod.MANUAL ? "DigiLocker" : "the step-by-step path"),
					Map.of("method", String.valueOf(context.application().getMethod())));
		}
	}

	private static AddressInput validate(AddressInput input) {
		if (input == null) {
			throw ValidationException.field("line1", "Enter your address");
		}
		String line1 = text(input.line1(), 120);
		String city = text(input.city(), 60);
		String state = text(input.state(), 60);
		String pincode = input.pincode() == null ? "" : input.pincode().trim();
		Map<String, String> problems = new LinkedHashMap<>();
		if (line1 == null) {
			problems.put("line1", "Enter your house and street");
		}
		if (city == null) {
			problems.put("city", "Enter your city or village");
		}
		if (state == null) {
			problems.put("state", "Choose your state");
		}
		if (!PINCODE.matcher(pincode).matches()) {
			problems.put("pincode", "Enter a valid 6-digit pincode");
		}
		if (!problems.isEmpty()) {
			throw new ValidationException("Check your address", problems);
		}
		return new AddressInput(line1, text(input.line2(), 120), city, text(input.district(), 60), state, pincode);
	}

	private static String text(String value, int max) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String clean = value.strip().replaceAll("\\s+", " ");
		if (clean.length() > max) {
			throw ValidationException.field("address", "Address lines can be at most " + max + " characters");
		}
		return clean;
	}

	private static String join(String a, String b) {
		String joined = ((a == null ? "" : a.trim()) + " " + (b == null ? "" : b.trim())).trim();
		return joined.isEmpty() ? null : joined;
	}
}
