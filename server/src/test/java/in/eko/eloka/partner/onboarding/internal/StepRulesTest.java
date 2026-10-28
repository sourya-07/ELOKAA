package in.eko.eloka.partner.onboarding.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import in.eko.eloka.partner.onboarding.PartnerStep;
import in.eko.eloka.partner.onboarding.StepStatus;
import in.eko.eloka.partner.shared.error.ApiProblem;
import org.junit.jupiter.api.Test;

/** Prerequisites, invalidation and attempt limits, without a database. */
class StepRulesTest {

	private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

	@Test
	void everyPrerequisiteComesEarlierInTheJourney() {
		for (PartnerStep step : PartnerStep.values()) {
			assertThat(step.prerequisites()).allMatch(p -> p.ordinal() < step.ordinal());
			assertThat(step.invalidates()).allMatch(d -> d.ordinal() > step.ordinal());
		}
	}

	@Test
	void aNewPanResetsTheCreditCheckAndTheDeclaration() {
		StepContext context = contextWithEverythingCompleted();
		context.complete(PartnerStep.PAN, "{}");
		assertThat(context.step(PartnerStep.CREDIT_CHECK).getStatus()).isEqualTo(StepStatus.IN_PROGRESS);
		assertThat(context.step(PartnerStep.DECLARATION).getStatus()).isEqualTo(StepStatus.IN_PROGRESS);
		assertThat(context.step(PartnerStep.PHOTOS).getStatus()).isEqualTo(StepStatus.COMPLETED);
	}

	@Test
	void aNewDigiLockerIdentityResetsThePanThroughToTheDeclaration() {
		StepContext context = contextWithEverythingCompleted();
		context.complete(PartnerStep.AADHAAR, "{}", new byte[32]);
		for (PartnerStep reset : List.of(PartnerStep.PAN, PartnerStep.CREDIT_CHECK, PartnerStep.ALTERNATE_ID,
				PartnerStep.KYC_DOCUMENTS, PartnerStep.DECLARATION)) {
			assertThat(context.step(reset).getStatus()).as(reset.name()).isEqualTo(StepStatus.IN_PROGRESS);
		}
		assertThat(context.step(PartnerStep.HOME_ADDRESS).getStatus()).isEqualTo(StepStatus.COMPLETED);
		assertThat(context.step(PartnerStep.EDUCATION).getStatus()).isEqualTo(StepStatus.COMPLETED);
	}

	@Test
	void anUnderAgeResultBlocksAtOnce() {
		ApplicationStep step = new ApplicationStep(UUID.randomUUID(), PartnerStep.AADHAAR, 3);
		step.block("DIGILOCKER_UNDER_AGE");
		assertThat(step.getStatus()).isEqualTo(StepStatus.BLOCKED);
		assertThat(step.getLastErrorCode()).isEqualTo("DIGILOCKER_UNDER_AGE");
	}

	@Test
	void theSamePanAgainKeepsTheCreditCheck() {
		byte[] pan = new byte[32];
		pan[0] = 1;
		List<ApplicationStep> rows = steps();
		rows.forEach(step -> step.complete("{}", NOW));
		rows.stream().filter(step -> step.getStepKey() == PartnerStep.PAN).forEach(step -> step.complete("{}", NOW, pan.clone()));
		StepContext context = new StepContext(application(), rows, mock(TimelineEntryRepository.class), NOW);

		context.complete(PartnerStep.PAN, "{}", pan.clone());
		assertThat(context.step(PartnerStep.CREDIT_CHECK).getStatus()).isEqualTo(StepStatus.COMPLETED);
		assertThat(context.step(PartnerStep.DECLARATION).getStatus()).isEqualTo(StepStatus.COMPLETED);

		byte[] otherPan = pan.clone();
		otherPan[1] = 7;
		context.complete(PartnerStep.PAN, "{}", otherPan);
		assertThat(context.step(PartnerStep.CREDIT_CHECK).getStatus()).isEqualTo(StepStatus.IN_PROGRESS);
		assertThat(context.step(PartnerStep.DECLARATION).getStatus()).isEqualTo(StepStatus.IN_PROGRESS);
	}

	@Test
	void missingPrerequisitesAreNamed() {
		StepContext context = new StepContext(application(), steps(), mock(TimelineEntryRepository.class), NOW);
		assertThatThrownBy(() -> context.requirePrerequisites(PartnerStep.CREDIT_CHECK))
			.isInstanceOf(ApiProblem.class)
			.hasMessageContaining("earlier steps");
	}

	@Test
	void aStepBlocksAfterItsAttemptsAndThenRefusesMore() {
		ApplicationStep step = new ApplicationStep(UUID.randomUUID(), PartnerStep.PAN, 3);
		step.fail("PAN_MISMATCH");
		step.failTechnically("PROVIDER_UNAVAILABLE"); // does not use an attempt
		step.fail("PAN_MISMATCH");
		assertThat(step.getStatus()).isEqualTo(StepStatus.FAILED);
		step.fail("PAN_MISMATCH");
		assertThat(step.getStatus()).isEqualTo(StepStatus.BLOCKED);
		assertThatThrownBy(() -> step.complete("{}", NOW)).isInstanceOf(ApiProblem.class)
			.hasMessageContaining("Too many failed attempts");
	}

	private static StepContext contextWithEverythingCompleted() {
		List<ApplicationStep> rows = steps();
		rows.forEach(step -> step.complete("{}", NOW));
		return new StepContext(application(), rows, mock(TimelineEntryRepository.class), NOW);
	}

	private static List<ApplicationStep> steps() {
		UUID id = UUID.randomUUID();
		return Arrays.stream(PartnerStep.values()).map(key -> new ApplicationStep(id, key, 3)).toList();
	}

	private static PartnerApplication application() {
		return new PartnerApplication("ptn_TEST", UUID.randomUUID(), NOW, Duration.ofDays(30));
	}
}
