package in.eko.eloka.partner.verification.internal;

import java.time.LocalDate;
import in.eko.eloka.partner.integration.DrivingLicenceVerifier;
import in.eko.eloka.partner.integration.IdDocumentCheck;
import in.eko.eloka.partner.integration.NameMatcher;
import in.eko.eloka.partner.integration.ProviderUnavailableException;
import in.eko.eloka.partner.integration.VoterIdVerifier;
import in.eko.eloka.partner.verification.VerificationApi.AlternateResult;
import in.eko.eloka.partner.verification.VerificationApi.AlternateType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The rules for the second identity document: a record must exist, a licence must be active, the
 * date of birth (where the record has one) must be the DigiLocker one, and the name must match the
 * DigiLocker name with at least {@code eloka.verification.name-match-min-score} (EPS Name Match:
 * 0.85 and up is a "good" match; docs/OPEN_QUESTIONS.md Q-23). An outage is never a result.
 */
@Component
class AlternateIdChecks {

	/** @param providerRef our reference of the record call; holder name and DOB as on the record */
	record Result(AlternateResult result, String providerRef, String holderName, LocalDate holderDob) {
	}

	private final VoterIdVerifier voterIds;
	private final DrivingLicenceVerifier licences;
	private final NameMatcher names;
	private final double minScore;

	AlternateIdChecks(VoterIdVerifier voterIds, DrivingLicenceVerifier licences, NameMatcher names,
			@Value("${eloka.verification.name-match-min-score:0.85}") double minScore) {
		this.voterIds = voterIds;
		this.licences = licences;
		this.names = names;
		this.minScore = minScore;
	}

	Result check(AlternateType type, String number, String aadhaarName, LocalDate aadhaarDob) {
		try {
			IdDocumentCheck record = type == AlternateType.VOTER_ID ? voterIds.verify(number)
					: licences.verify(number, aadhaarDob);
			return switch (record) {
				case IdDocumentCheck.NotFound notFound -> new Result(AlternateResult.NOT_FOUND, null, null, null);
				case IdDocumentCheck.DobMismatch mismatch -> new Result(AlternateResult.DOB_MISMATCH, null, null, null);
				case IdDocumentCheck.Found found -> judge(found, aadhaarName, aadhaarDob);
			};
		}
		catch (ProviderUnavailableException ex) {
			return new Result(AlternateResult.UNAVAILABLE, null, null, null);
		}
	}

	private Result judge(IdDocumentCheck.Found found, String aadhaarName, LocalDate aadhaarDob) {
		AlternateResult result;
		if (!found.active()) {
			result = AlternateResult.NOT_ACTIVE;
		}
		else if (found.dob() != null && !found.dob().equals(aadhaarDob)) {
			result = AlternateResult.DOB_MISMATCH;
		}
		else if (names.score(found.name(), aadhaarName) < minScore) {
			result = AlternateResult.NAME_MISMATCH;
		}
		else {
			result = AlternateResult.VERIFIED;
		}
		return new Result(result, found.providerRef(), found.name(), found.dob());
	}
}
