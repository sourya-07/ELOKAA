package in.eko.eloka.partner.documents;

import java.util.Set;

/**
 * What a file is, and the rules it must meet (owner's rules of 2026-10-29, docs/OPEN_QUESTIONS.md
 * C-9): photos are JPG/JPEG or PNG, 20 KB to 2 MB and at least 300 px on each side; Aadhaar images,
 * PAN card, ID images, marksheets and certificates are JPG/JPEG, PNG or PDF, 10 KB to 5 MB.
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

	/** Accepted formats (detected from the bytes) and limits, in bytes of the uploaded file. */
	public record Rules(Set<String> contentTypes, long minBytes, long maxBytes, int minImageSide) {

		private static final long KB = 1024;
		private static final long MB = 1024 * KB;

		static final Rules PHOTO = new Rules(Set.of("image/jpeg", "image/png"), 20 * KB, 2 * MB, 300);
		static final Rules PAPER = new Rules(Set.of("image/jpeg", "image/png", "application/pdf"), 10 * KB, 5 * MB, 0);

		public boolean acceptsPdf() {
			return contentTypes.contains("application/pdf");
		}

		/** "JPG or PNG" / "JPG, PNG or PDF", as the messages name the formats. */
		public String formatNames() {
			return acceptsPdf() ? "JPG, PNG or PDF" : "JPG or PNG";
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
