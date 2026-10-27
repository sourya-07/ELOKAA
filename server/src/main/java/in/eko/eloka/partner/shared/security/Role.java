package in.eko.eloka.partner.shared.security;

/** Role of a caller. Derived from the session, never from request data. */
public enum Role {
	PARTNER_APPLICANT,
	REVIEWER,
	APPROVER
}
