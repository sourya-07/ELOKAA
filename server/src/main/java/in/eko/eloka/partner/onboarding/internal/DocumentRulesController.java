package in.eko.eloka.partner.onboarding.internal;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.eko.eloka.partner.documents.DocumentKind;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The file rules the server enforces, so the pages state the real formats and limits instead of a
 * copy that could drift. Rules only: no personal data, the same for every applicant.
 */
@RestController
class DocumentRulesController {

	/** Kinds an applicant may still upload (the masked Aadhaar kinds are from before journey v2). */
	private static final Set<DocumentKind> RETIRED = EnumSet.of(DocumentKind.AADHAAR_MASKED_FRONT,
			DocumentKind.AADHAAR_MASKED_BACK);

	record Rule(String kind, List<String> formats, @JsonProperty("max_bytes") long maxBytes,
			@JsonProperty("min_side_px") int minSidePx, @JsonProperty("max_count") int maxCount) {
	}

	@GetMapping("/v1/partner/document-rules")
	List<Rule> rules() {
		return Arrays.stream(DocumentKind.values())
			.filter(kind -> !RETIRED.contains(kind))
			.map(kind -> new Rule(kind.name(), kind.rules().contentTypes().stream().sorted().toList(), kind.rules().maxBytes(),
					kind.rules().minImageSide(), kind == DocumentKind.COMPUTER_CERTIFICATE ? DocumentSteps.MAX_COMPUTER_CERTIFICATES : 1))
			.toList();
	}
}
