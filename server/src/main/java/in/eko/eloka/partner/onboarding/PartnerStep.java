package in.eko.eloka.partner.onboarding;

import java.util.List;

/**
 * The steps of a partner application in journey order, with their prerequisites and the
 * steps they make stale when redone (docs/PARTNER_JOURNEY.md, "Steps"). The server enforces
 * the prerequisites; the order is the order the client shows.
 */
public enum PartnerStep {
	MOBILE_VERIFICATION,
	CONSENT,
	VERIFICATION_METHOD,
	AADHAAR,
	PAN,
	CREDIT_CHECK,
	HOME_ADDRESS,
	PHOTOS,
	EDUCATION,
	ID_DOCUMENT,
	DECLARATION;

	/** Steps that must be satisfied before this one can be started. */
	public List<PartnerStep> prerequisites() {
		return switch (this) {
			case MOBILE_VERIFICATION -> List.of();
			case CONSENT -> List.of(MOBILE_VERIFICATION);
			case VERIFICATION_METHOD -> List.of(CONSENT);
			case AADHAAR -> List.of(VERIFICATION_METHOD);
			case PAN -> List.of(AADHAAR);
			case CREDIT_CHECK -> List.of(PAN);
			case HOME_ADDRESS -> List.of(CREDIT_CHECK);
			case PHOTOS, EDUCATION, ID_DOCUMENT -> List.of(CREDIT_CHECK, HOME_ADDRESS);
			case DECLARATION -> List.of(CONSENT, VERIFICATION_METHOD, AADHAAR, PAN, CREDIT_CHECK, HOME_ADDRESS, PHOTOS,
					EDUCATION, ID_DOCUMENT);
		};
	}

	/**
	 * Steps whose result is no longer valid when this step is completed again with new data.
	 * They go back to {@code IN_PROGRESS}, and so do the steps they in turn invalidate. A new
	 * Aadhaar result invalidates the PAN step (its name match) and so the credit check; the
	 * eligibility module may reuse a recent result for the same PAN instead of asking the bureau again.
	 */
	public List<PartnerStep> invalidates() {
		return switch (this) {
			case VERIFICATION_METHOD -> List.of(AADHAAR, HOME_ADDRESS, DECLARATION);
			case AADHAAR -> List.of(PAN, DECLARATION);
			case PAN -> List.of(CREDIT_CHECK, DECLARATION);
			case CONSENT, CREDIT_CHECK, HOME_ADDRESS, PHOTOS, EDUCATION, ID_DOCUMENT -> List.of(DECLARATION);
			case MOBILE_VERIFICATION, DECLARATION -> List.of();
		};
	}
}
