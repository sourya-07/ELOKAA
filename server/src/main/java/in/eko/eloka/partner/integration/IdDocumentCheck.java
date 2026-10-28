package in.eko.eloka.partner.integration;

import java.time.LocalDate;

/** What a government record check of a voter ID or driving licence answered. */
public sealed interface IdDocumentCheck {

	/**
	 * @param name holder's name on the record
	 * @param dob date of birth on the record, when given
	 * @param active false for a licence the authority marks as expired, suspended or similar
	 * @param providerRef our reference for this call
	 */
	record Found(String name, LocalDate dob, boolean active, String providerRef) implements IdDocumentCheck {
	}

	/** No record for this number. */
	record NotFound() implements IdDocumentCheck {
	}

	/** The authority says the date of birth does not belong to this licence. */
	record DobMismatch() implements IdDocumentCheck {
	}
}
