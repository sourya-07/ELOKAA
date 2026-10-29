package in.eko.eloka.partner.identity;

import java.util.Optional;
import java.util.UUID;

/** What other modules may read about an applicant: masked values only, never the mobile number itself. */
public interface ApplicantDirectory {

	/** The applicant's mobile as shown to people ({@code ******3210}); empty for an unknown id. */
	Optional<String> maskedMobile(UUID applicantId);
}
