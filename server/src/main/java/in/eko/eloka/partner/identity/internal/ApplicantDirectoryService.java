package in.eko.eloka.partner.identity.internal;

import java.util.Optional;
import java.util.UUID;
import in.eko.eloka.partner.identity.ApplicantDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads the masked copy kept at sign-in; the encrypted number is never decrypted here. */
@Service
@Transactional(readOnly = true)
class ApplicantDirectoryService implements ApplicantDirectory {

	private final ApplicantRepository applicants;

	ApplicantDirectoryService(ApplicantRepository applicants) {
		this.applicants = applicants;
	}

	@Override
	public Optional<String> maskedMobile(UUID applicantId) {
		return applicants.findById(applicantId).map(Applicant::getMobileMasked);
	}
}
