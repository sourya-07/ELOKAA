package in.eko.eloka.partner.onboarding.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.eko.eloka.partner.documents.DocumentKind;
import in.eko.eloka.partner.onboarding.PartnerStep;
import in.eko.eloka.partner.shared.error.ApiProblem;
import org.springframework.http.HttpStatus;

/**
 * Which files each step needs, in one place: the step submits, the applicant's checklist in the
 * snapshot and the review page all read these rules, so they cannot disagree. A requirement is
 * met by any one of its options; an option needs all of its kinds (both sides of a card).
 */
final class DocumentRequirements {

	/** One line of the checklist. */
	record Requirement(String group, PartnerStep step, List<List<DocumentKind>> options) {

		boolean metBy(Map<DocumentKind, Long> have) {
			return options.stream().anyMatch(option -> option.stream().allMatch(have::containsKey));
		}

		/** The kinds still missing from the option closest to done (what to ask for). */
		List<DocumentKind> missing(Map<DocumentKind, Long> have) {
			List<DocumentKind> best = null;
			for (List<DocumentKind> option : options) {
				List<DocumentKind> gap = option.stream().filter(kind -> !have.containsKey(kind)).toList();
				if (best == null || gap.size() < best.size()) {
					best = gap;
				}
			}
			return best == null ? List.of() : best;
		}
	}

	/** The checklist as the applicant sees it. */
	record Status(String group, @JsonProperty("step_key") String stepKey, String rule, List<List<String>> options,
			boolean satisfied) {
	}

	static final List<Requirement> ALL = List.of(
			new Requirement("LIVE_PHOTO", PartnerStep.PHOTOS, options(List.of(DocumentKind.LIVE_PHOTO))),
			new Requirement("PASSPORT_PHOTO", PartnerStep.PHOTOS, options(List.of(DocumentKind.PASSPORT_PHOTO))),
			new Requirement("AADHAAR_FRONT", PartnerStep.KYC_DOCUMENTS, options(List.of(DocumentKind.AADHAAR_FRONT))),
			new Requirement("AADHAAR_BACK", PartnerStep.KYC_DOCUMENTS, options(List.of(DocumentKind.AADHAAR_BACK))),
			new Requirement("PAN_CARD", PartnerStep.KYC_DOCUMENTS, options(List.of(DocumentKind.PAN_CARD))),
			// Which pair counts is narrowed to the verified document at submit (DocumentSteps)
			new Requirement("VOTER_ID_OR_DRIVING_LICENCE", PartnerStep.KYC_DOCUMENTS,
					options(List.of(DocumentKind.VOTER_ID_FRONT, DocumentKind.VOTER_ID_BACK),
							List.of(DocumentKind.DRIVING_LICENCE_FRONT, DocumentKind.DRIVING_LICENCE_BACK))),
			new Requirement("CLASS_10_MARKSHEET", PartnerStep.EDUCATION, options(List.of(DocumentKind.CLASS_10_MARKSHEET))),
			new Requirement("CLASS_12_MARKSHEET", PartnerStep.EDUCATION, options(List.of(DocumentKind.CLASS_12_MARKSHEET))),
			new Requirement("GRADUATION_OR_DIPLOMA", PartnerStep.EDUCATION,
					options(List.of(DocumentKind.GRADUATION_CERTIFICATE), List.of(DocumentKind.DIPLOMA_CERTIFICATE))));

	private DocumentRequirements() {
	}

	static List<Requirement> forStep(PartnerStep step) {
		return ALL.stream().filter(requirement -> requirement.step() == step).toList();
	}

	/** {@code 409 DOCUMENTS_MISSING} with the missing kinds, unless every requirement of {@code step} is met. */
	static void requireMet(PartnerStep step, Map<DocumentKind, Long> have) {
		List<String> missing = new ArrayList<>();
		for (Requirement requirement : forStep(step)) {
			if (!requirement.metBy(have)) {
				requirement.missing(have).forEach(kind -> missing.add(kind.name()));
			}
		}
		if (!missing.isEmpty()) {
			throw new ApiProblem(HttpStatus.CONFLICT, "DOCUMENTS_MISSING", "Some required files are missing",
					Map.of("missing_documents", missing));
		}
	}

	static List<Status> status(Map<DocumentKind, Long> have) {
		return ALL.stream()
			.map(requirement -> new Status(requirement.group(), requirement.step().name(),
					requirement.options().size() > 1 ? "ONE_OF" : "ALL",
					requirement.options().stream().map(option -> option.stream().map(Enum::name).toList()).toList(),
					requirement.metBy(have)))
			.toList();
	}

	@SafeVarargs
	private static List<List<DocumentKind>> options(List<DocumentKind>... options) {
		return Arrays.asList(options);
	}
}
