package in.eko.eloka.partner.onboarding.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import in.eko.eloka.partner.audit.AuditEntry;
import in.eko.eloka.partner.audit.AuditLog;
import in.eko.eloka.partner.documents.DocumentKind;
import in.eko.eloka.partner.onboarding.PartnerStep;
import in.eko.eloka.partner.onboarding.StepStatus;
import in.eko.eloka.partner.shared.crypto.BlindIndex;
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
	private final BlindIndex index;
	private final AuditLog audit;
	private final JsonMapper json;

	IdentitySteps(ApplicationService applications, DocumentSteps documents, VerificationApi verification,
			ConsentRecordRepository consents, PartnerAddressRepository addresses,
			PartnerApplicationRepository applicationRows, OnboardingProperties properties, FieldCipher cipher,
			BlindIndex index, AuditLog audit, JsonMapper json) {
		this.applications = applications;
		this.documents = documents;
		this.verification = verification;
		this.consents = consents;
		this.addresses = addresses;
		this.applicationRows = applicationRows;
		this.properties = properties;
		this.cipher = cipher;
		this.index = index;
		this.audit = audit;
		this.json = json;
	}

	/** Sends the applicant to DigiLocker. Needs the DigiLocker consent first. */
	@Transactional
	String startDigiLocker(UUID applicantId, String publicId, long version) {
		return applications.write(applicantId, publicId, version, context -> {
			context.requirePrerequisites(PartnerStep.AADHAAR);
			boolean consented = consents
				.findFirstByApplicationIdAndPurposeOrderByAcceptedAtDesc(context.applicationId(), ConsentRecord.Purpose.DIGILOCKER)
				.filter(consent -> consent.getTextVersion().equals(properties.consentVersion(ConsentRecord.Purpose.DIGILOCKER)))
				.isPresent();
			if (!consented) {
				throw new ApiProblem(HttpStatus.CONFLICT, "CONSENT_REQUIRED", "Allow ELOKA to fetch your details from DigiLocker first",
						Map.of("purpose", "DIGILOCKER"));
			}
			StepStatus status = context.step(PartnerStep.AADHAAR).getStatus();
			if (status == StepStatus.LOCKED) {
				throw new ApiProblem(HttpStatus.CONFLICT, "STEP_NOT_EDITABLE", "Your Aadhaar cannot be changed now");
			}
			if (status == StepStatus.BLOCKED) { // before the provider call: no DigiLocker session for a blocked step
				throw new ApiProblem(HttpStatus.LOCKED, "STEP_BLOCKED", "Too many failed attempts on this step. Please contact support.");
			}
			String url = verification.startDigiLocker(context.applicationId(), properties.digilockerReturnUrl()).url();
			// A redo of a done (or reopened) step keeps that status until DigiLocker answers: leaving
			// half-way changes nothing, and the steps after it stay valid
			if (status != StepStatus.COMPLETED && status != StepStatus.REOPENED) {
				context.awaitExternal(PartnerStep.AADHAAR);
			}
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
			VerificationApi.DigiLockerOutcome outcome = verification.completeDigiLocker(context.applicationId(), state);
			String result = switch (outcome) {
				case VerificationApi.DigiLockerOutcome.Pending p -> null;
				case VerificationApi.DigiLockerOutcome.Unavailable u -> "UNAVAILABLE";
				case VerificationApi.DigiLockerOutcome.Failed failed -> failed.code();
				case VerificationApi.DigiLockerOutcome.Verified verified -> "VERIFIED";
			};
			if (result != null) {
				audit.record(new AuditEntry("DIGILOCKER_RESULT", "APPLICANT", applicantId.toString(), "PARTNER_APPLICATION",
						publicId, Map.of("result", result)));
			}
			switch (outcome) {
				case VerificationApi.DigiLockerOutcome.Pending p -> {
					return true;
				}
				// DigiLocker or EPS could not answer: not a result. Nothing is used up, the trip stays open and
				// the answer is "still waiting" (202); a done or reopened step keeps its status
				case VerificationApi.DigiLockerOutcome.Unavailable u -> {
					StepStatus current = context.step(PartnerStep.AADHAAR).getStatus();
					if (current != StepStatus.COMPLETED && current != StepStatus.REOPENED) {
						context.failTechnically(PartnerStep.AADHAAR, "DIGILOCKER_UNAVAILABLE");
					}
					return true;
				}
				// Under 18: no retry can change it (docs/OPEN_QUESTIONS.md Q-29)
				case VerificationApi.DigiLockerOutcome.Failed failed when failed.code().equals("UNDER_AGE") ->
					context.block(PartnerStep.AADHAAR, "DIGILOCKER_UNDER_AGE");
				case VerificationApi.DigiLockerOutcome.Failed failed -> context.fail(PartnerStep.AADHAAR, "DIGILOCKER_" + failed.code());
				case VerificationApi.DigiLockerOutcome.Verified verified -> {
					boolean addressComplete = saveDigiLockerAddress(context, verified.address());
					Map<String, Object> summary = new LinkedHashMap<>();
					summary.put("method", "DIGILOCKER");
					summary.put("aadhaar_masked", "XXXX-XXXX-" + verified.aadhaarLast4());
					summary.put("address_from_digilocker", addressComplete);
					context.complete(PartnerStep.AADHAAR, json.writeValueAsString(summary), verified.identityHash());
					if (addressComplete) {
						completeAddressFromDigiLocker(context);
					}
				}
			}
			return false;
		});
		return new DigiLockerResult(pending, applications.get(applicantId, publicId));
	}

	/**
	 * Checks the PAN against the name and date of birth from DigiLocker (never what was typed). A PAN
	 * held by another open, approved or ineligible application is refused.
	 */
	@Transactional
	ApplicationSnapshot verifyPan(UUID applicantId, String publicId, long version, String pan) {
		applications.write(applicantId, publicId, version, context -> {
			context.requirePrerequisites(PartnerStep.PAN);
			if (context.step(PartnerStep.PAN).getStatus() == StepStatus.BLOCKED) {
				throw new ApiProblem(HttpStatus.LOCKED, "STEP_BLOCKED", "Too many failed attempts on this step. Please contact support.");
			}
			VerificationApi.PanOutcome outcome = verification.verifyPan(context.applicationId(), pan);
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
			context.complete(PartnerStep.PAN, json.writeValueAsString(Map.of("pan_masked", outcome.panMasked())),
					outcome.panHash());
			return null;
		});
		return applications.get(applicantId, publicId);
	}

	/**
	 * The second identity document (voter ID or driving licence), checked with its government record
	 * and against the DigiLocker identity. Each type has its own tries; the step blocks when both are
	 * used up (staff follow up, nothing is verified from an image alone). An outage changes nothing.
	 */
	@Transactional
	ApplicationSnapshot verifyAlternateId(UUID applicantId, String publicId, long version, String typeName, String number) {
		VerificationApi.AlternateType type = parseType(typeName);
		applications.write(applicantId, publicId, version, context -> {
			context.requirePrerequisites(PartnerStep.ALTERNATE_ID);
			ApplicationStep step = context.step(PartnerStep.ALTERNATE_ID);
			if (step.getStatus() == StepStatus.BLOCKED) {
				throw new ApiProblem(HttpStatus.LOCKED, "STEP_BLOCKED", "Too many failed attempts on this step. Please contact support.");
			}
			if (verification.alternateIdFailures(context.applicationId()).getOrDefault(type, 0L) >= properties.maxAttempts()) {
				throw new ApiProblem(HttpStatus.CONFLICT, "ID_TYPE_EXHAUSTED",
						"This document could not be verified. Try the other one.", Map.of("type", type.name()));
			}
			VerificationApi.AlternateIdOutcome outcome = verification.verifyAlternateId(context.applicationId(), type, number);
			if (outcome.result() == VerificationApi.AlternateResult.UNAVAILABLE) {
				// An outage is not a result: a done or reopened step keeps its status
				if (step.getStatus() != StepStatus.COMPLETED && step.getStatus() != StepStatus.REOPENED) {
					context.failTechnically(PartnerStep.ALTERNATE_ID, "ALTERNATE_ID_UNAVAILABLE");
				}
				return null;
			}
			String result = outcome.result() != VerificationApi.AlternateResult.VERIFIED ? type.name() + "_" + outcome.result().name()
					: applicationRows.existsBlockingIdDocument(outcome.numberHash(), context.applicationId(), applications.blockingStatuses())
							? "ID_ALREADY_USED" : "VERIFIED";
			audit.record(new AuditEntry("ALTERNATE_ID_CHECKED", "APPLICANT", applicantId.toString(), "PARTNER_APPLICATION",
					publicId, Map.of("type", type.name(), "result", result)));
			if (!result.equals("VERIFIED")) {
				context.fail(PartnerStep.ALTERNATE_ID, result);
				return null;
			}
			if (type == VerificationApi.AlternateType.VOTER_ID) {
				context.application().bindVoterId(outcome.numberHash());
			}
			else {
				context.application().bindDrivingLicence(outcome.numberHash());
			}
			context.complete(PartnerStep.ALTERNATE_ID,
					json.writeValueAsString(Map.of("type", type.name(), "number_masked", outcome.numberMasked())),
					index.hash("alternate-id", type.name() + ":" + java.util.HexFormat.of().formatHex(outcome.numberHash())));
			return null;
		});
		return applications.get(applicantId, publicId);
	}

	private static VerificationApi.AlternateType parseType(String type) {
		try {
			return VerificationApi.AlternateType.valueOf(type == null ? "" : type.trim().toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			throw ValidationException.field("type", "Choose voter ID or driving licence");
		}
	}

	/**
	 * Home address typed by the applicant: only when DigiLocker did not give a whole one, or a
	 * reviewer asked for it to be corrected. A DigiLocker address is not replaced by a typed one.
	 */
	@Transactional
	ApplicationSnapshot saveHomeAddress(UUID applicantId, String publicId, long version, AddressInput input) {
		applications.write(applicantId, publicId, version, context -> {
			context.requirePrerequisites(PartnerStep.HOME_ADDRESS);
			boolean fromDigiLocker = addresses.findByApplicationId(context.applicationId())
				.map(existing -> existing.getSource() == PartnerAddress.Source.DIGILOCKER)
				.orElse(false);
			if (fromDigiLocker && context.step(PartnerStep.HOME_ADDRESS).getStatus() != StepStatus.REOPENED) {
				throw new ApiProblem(HttpStatus.CONFLICT, "ADDRESS_FROM_DIGILOCKER",
						"Your home address comes from DigiLocker. To change it, update it in Aadhaar and connect DigiLocker again.");
			}
			AddressInput clean = validate(input);
			PartnerAddress address = addresses.findByApplicationId(context.applicationId())
				.orElseGet(() -> new PartnerAddress(context.applicationId()));
			address.set(cipher.encrypt(clean.line1()), clean.line2() == null ? null : cipher.encrypt(clean.line2()),
					cipher.encrypt(clean.city()), clean.district(), clean.state(), clean.pincode(),
					PartnerAddress.Source.SELF_DECLARED);
			addresses.save(address);
			context.complete(PartnerStep.HOME_ADDRESS, addressSummary(address), fingerprint(address));
			return null;
		});
		return applications.get(applicantId, publicId);
	}

	/**
	 * Completes the home address step from the DigiLocker address just saved. Again with the same
	 * address changes nothing; a frozen step (during a correction) is left alone.
	 */
	private void completeAddressFromDigiLocker(StepContext context) {
		if (context.step(PartnerStep.HOME_ADDRESS).getStatus() == StepStatus.LOCKED) {
			return;
		}
		addresses.findByApplicationId(context.applicationId())
			.filter(address -> address.getSource() == PartnerAddress.Source.DIGILOCKER)
			.ifPresent(address -> context.complete(PartnerStep.HOME_ADDRESS, addressSummary(address), fingerprint(address)));
	}

	private boolean saveDigiLockerAddress(StepContext context, VerificationApi.Address from) {
		String line1 = join(from.line1(), from.line2());
		if (line1 == null || from.city() == null || from.state() == null || from.pincode() == null
				|| !PINCODE.matcher(from.pincode()).matches()) {
			return false; // the applicant types it on the address page
		}
		if (context.step(PartnerStep.HOME_ADDRESS).getStatus() == StepStatus.LOCKED) {
			// Frozen during a correction: never overwrite the address (or a reviewer's decision on it)
			return addresses.findByApplicationId(context.applicationId())
				.map(existing -> existing.getSource() == PartnerAddress.Source.DIGILOCKER)
				.orElse(false);
		}
		PartnerAddress address = addresses.findByApplicationId(context.applicationId())
			.orElseGet(() -> new PartnerAddress(context.applicationId()));
		address.set(cipher.encrypt(line1), null, cipher.encrypt(from.city()), from.district(), from.state(),
				from.pincode(), PartnerAddress.Source.DIGILOCKER);
		addresses.save(address);
		return true;
	}

	/** The address as a blind index: the same address saved again does not reset later steps. */
	private byte[] fingerprint(PartnerAddress address) {
		String line2 = address.getLine2Enc() == null ? "" : cipher.decrypt(address.getLine2Enc());
		return index.hash("home-address", String.join("|", cipher.decrypt(address.getLine1Enc()), line2,
				cipher.decrypt(address.getCityEnc()), String.valueOf(address.getDistrict()), address.getState(),
				address.getPincode()).toUpperCase());
	}

	private String addressSummary(PartnerAddress address) {
		return json.writeValueAsString(Map.of("state", address.getState(), "pincode", address.getPincode(), "source",
				address.getSource().name()));
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
