package in.eko.eloka.partner.onboarding.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.Map;
import in.eko.eloka.partner.documents.DocumentKind;
import in.eko.eloka.partner.onboarding.PartnerStep;
import in.eko.eloka.partner.shared.error.ApiProblem;
import org.junit.jupiter.api.Test;

/** The file checklist: one rule set for the step submits, the snapshot and the review. */
class DocumentRequirementsTest {

	@Test
	void eitherCardCountsButOnlyWithBothSides() {
		Map<DocumentKind, Long> have = new EnumMap<>(DocumentKind.class);
		have.put(DocumentKind.AADHAAR_FRONT, 1L);
		have.put(DocumentKind.AADHAAR_BACK, 1L);
		have.put(DocumentKind.PAN_CARD, 1L);
		have.put(DocumentKind.VOTER_ID_FRONT, 1L);
		have.put(DocumentKind.DRIVING_LICENCE_FRONT, 1L);
		assertThatThrownBy(() -> DocumentRequirements.requireMet(PartnerStep.KYC_DOCUMENTS, have))
			.isInstanceOf(ApiProblem.class)
			.satisfies(e -> assertThat(((ApiProblem) e).getProperties().get("missing_documents")).asList().hasSize(1));

		have.put(DocumentKind.DRIVING_LICENCE_BACK, 1L);
		DocumentRequirements.requireMet(PartnerStep.KYC_DOCUMENTS, have);
	}

	@Test
	void theChecklistSaysWhatIsMetAndHow() {
		Map<DocumentKind, Long> have = new EnumMap<>(DocumentKind.class);
		have.put(DocumentKind.LIVE_PHOTO, 1L);
		var status = DocumentRequirements.status(have);
		assertThat(status).filteredOn(s -> s.group().equals("LIVE_PHOTO")).singleElement()
			.satisfies(s -> assertThat(s.satisfied()).isTrue());
		assertThat(status).filteredOn(s -> s.group().equals("VOTER_ID_OR_DRIVING_LICENCE")).singleElement()
			.satisfies(s -> {
				assertThat(s.rule()).isEqualTo("ONE_OF");
				assertThat(s.satisfied()).isFalse();
			});
	}

	@Test
	void graduationOrDiplomaIsEnough() {
		Map<DocumentKind, Long> have = new EnumMap<>(DocumentKind.class);
		have.put(DocumentKind.CLASS_10_MARKSHEET, 1L);
		have.put(DocumentKind.CLASS_12_MARKSHEET, 1L);
		assertThatThrownBy(() -> DocumentRequirements.requireMet(PartnerStep.EDUCATION, have)).isInstanceOf(ApiProblem.class);
		have.put(DocumentKind.DIPLOMA_CERTIFICATE, 1L);
		DocumentRequirements.requireMet(PartnerStep.EDUCATION, have);
	}
}
