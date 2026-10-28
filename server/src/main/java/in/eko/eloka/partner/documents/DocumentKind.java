package in.eko.eloka.partner.documents;

import java.util.Set;

/**
 * What a file is, and the rules it must meet (docs/OPEN_QUESTIONS.md Q-12): photos are JPEG
 * only, at most 5 MB and at least 300 px on each side; Aadhaar images, PAN card, certificates,
 * marksheets and ID images are JPEG or PDF, at most 10 MB.
 */
public enum DocumentKind {
	LIVE_PHOTO(Rules.PHOTO),
	PASSPORT_PHOTO(Rules.PHOTO),
	/** Before journey v2 only (masked images on the step-by-step path); no longer accepted. */
	AADHAAR_MASKED_FRONT(Rules.PAPER),
	/** Before journey v2 only; no longer accepted. */
	AADHAAR_MASKED_BACK(Rules.PAPER),
	/** The full, unmasked card (ADR-0008): restricted staff viewing, never read by machine. */
	AADHAAR_FRONT(Rules.PAPER),
	AADHAAR_BACK(Rules.PAPER),
	PAN_CARD(Rules.PAPER),
	CLASS_10_MARKSHEET(Rules.PAPER),
	CLASS_12_MARKSHEET(Rules.PAPER),
	GRADUATION_CERTIFICATE(Rules.PAPER),
	DIPLOMA_CERTIFICATE(Rules.PAPER),
	COMPUTER_CERTIFICATE(Rules.PAPER),
	VOTER_ID_FRONT(Rules.PAPER),
	VOTER_ID_BACK(Rules.PAPER),
	DRIVING_LICENCE_FRONT(Rules.PAPER),
	DRIVING_LICENCE_BACK(Rules.PAPER);

	/** Accepted formats and limits. */
	public record Rules(Set<String> contentTypes, long maxBytes, int minImageSide) {

		static final Rules PHOTO = new Rules(Set.of("image/jpeg"), 5L * 1024 * 1024, 300);
		static final Rules PAPER = new Rules(Set.of("image/jpeg", "application/pdf"), 10L * 1024 * 1024, 0);

		public boolean acceptsPdf() {
			return contentTypes.contains("application/pdf");
		}
	}

	private final Rules rules;

	DocumentKind(Rules rules) {
		this.rules = rules;
	}

	public Rules rules() {
		return rules;
	}

	/** A full Aadhaar image: only the reviewer on the case or an approver may open it (ADR-0008). */
	public boolean isFullAadhaar() {
		return this == AADHAAR_FRONT || this == AADHAAR_BACK;
	}
}
