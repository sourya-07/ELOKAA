package in.eko.eloka.partner.onboarding;

import java.util.List;

/**
 * The steps of a partner application in journey order, with their prerequisites and the
 * steps they make stale when redone (docs/PARTNER_JOURNEY.md, "Steps"). The server enforces
 * the prerequisites; the order is the order the client shows.
 */
public enum PartnerStep {
	MOBILE_VERIFICATION,
	EMAIL_VERIFICATION,
	CONSENT,
	AADHAAR,
	HOME_ADDRESS,
	PAN,
	CREDIT_CHECK,
	ALTERNATE_ID,
	PHOTOS,
	EDUCATION,
	ID_DOCUMENT,
	DECLARATION;

	/** Steps that must be satisfied before this one can be started. */
	public List<PartnerStep> prerequisites() {
		return switch (this) {
			case MOBILE_VERIFICATION -> List.of();
			case EMAIL_VERIFICATION -> List.of(MOBILE_VERIFICATION);
			case CONSENT -> List.of(EMAIL_VERIFICATION);
			case AADHAAR -> List.of(CONSENT);
			case HOME_ADDRESS, PAN -> List.of(AADHAAR);
			case CREDIT_CHECK -> List.of(PAN);
			case ALTERNATE_ID -> List.of(CREDIT_CHECK);
			case PHOTOS, EDUCATION, ID_DOCUMENT -> List.of(CREDIT_CHECK, HOME_ADDRESS, ALTERNATE_ID);
			case DECLARATION -> List.of(EMAIL_VERIFICATION, CONSENT, AADHAAR, HOME_ADDRESS, PAN, CREDIT_CHECK, ALTERNATE_ID,
					PHOTOS, EDUCATION, ID_DOCUMENT);
		};
	}

	/**
	 * Steps whose result is no longer valid when this step is completed again with different data
	 * (the same data changes nothing, see the step fingerprint). They go back to {@code IN_PROGRESS},
	 * and so do the steps they in turn invalidate. A new DigiLocker identity invalidates the PAN step
	 * (its name and date-of-birth match) and so the credit check; the eligibility module may reuse a
	 * recent result for the same PAN instead of asking the bureau again.
	 */
	public List<PartnerStep> invalidates() {
		return switch (this) {
			// The PAN and the second ID were matched against the DigiLocker name and date of birth
			case AADHAAR -> List.of(PAN, ALTERNATE_ID, DECLARATION);
			// The ID images must be of the verified document
			case ALTERNATE_ID -> List.of(ID_DOCUMENT, DECLARATION);
			case PAN -> List.of(CREDIT_CHECK, DECLARATION);
			case EMAIL_VERIFICATION, CONSENT, HOME_ADDRESS, CREDIT_CHECK, PHOTOS, EDUCATION, ID_DOCUMENT -> List.of(DECLARATION);
			case MOBILE_VERIFICATION, DECLARATION -> List.of();
		};
	}
}
