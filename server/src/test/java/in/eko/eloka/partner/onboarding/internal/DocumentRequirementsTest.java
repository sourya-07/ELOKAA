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
		have.put(DocumentKind.VOTER_ID_FRONT, 1L);
		have.put(DocumentKind.DRIVING_LICENCE_FRONT, 1L);
		assertThatThrownBy(() -> DocumentRequirements.requireMet(PartnerStep.ID_DOCUMENT, have))
			.isInstanceOf(ApiProblem.class)
			.satisfies(e -> assertThat(((ApiProblem) e).getProperties().get("missing_documents")).asList().hasSize(1));

		have.put(DocumentKind.DRIVING_LICENCE_BACK, 1L);
		DocumentRequirements.requireMet(PartnerStep.ID_DOCUMENT, have);
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
}
