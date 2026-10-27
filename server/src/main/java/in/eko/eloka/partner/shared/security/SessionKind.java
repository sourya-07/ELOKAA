package in.eko.eloka.partner.shared.security;

/** What a session was created for. Each kind may only use its own routes. */
public enum SessionKind {
	/** Someone applying to become a partner ({@code /v1/partner}). */
	PARTNER_APPLICANT,
	/** Eko staff reviewing applications ({@code /v1/admin}). */
	STAFF
}
