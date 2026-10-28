package in.eko.eloka.partner.verification.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import in.eko.eloka.partner.integration.DigiLockerProvider;
import in.eko.eloka.partner.integration.PanVerifier;
import in.eko.eloka.partner.integration.ProviderUnavailableException;
import in.eko.eloka.partner.shared.crypto.BlindIndex;
import in.eko.eloka.partner.shared.crypto.FieldCipher;
import in.eko.eloka.partner.shared.crypto.Tokens;
import in.eko.eloka.partner.verification.EvidenceStatus;
import in.eko.eloka.partner.verification.VerificationApi.DigiLockerOutcome;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** What a DigiLocker answer becomes: an outage is not a result, and an applicant under 18 cannot pass. */
class DigiLockerCompletionTest {

	private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
	private static final UUID APPLICATION = UUID.randomUUID();

	private final VerificationRepository verifications = mock(VerificationRepository.class);
	private final DigiLockerSessionRepository sessions = mock(DigiLockerSessionRepository.class);
	private final DigiLockerProvider provider = mock(DigiLockerProvider.class);
	private final VerificationService service = new VerificationService(verifications, sessions, mock(PanVerifier.class),
			provider, mock(FieldCipher.class), mock(BlindIndex.class), Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void anOutageKeepsTheTripOpenSoItCanBeCheckedAgain() {
		DigiLockerSession session = openSession("dls_outage");
		when(provider.fetch(any(), any())).thenThrow(new ProviderUnavailableException("DigiLocker"));

		assertThat(service.completeDigiLocker(APPLICATION, "dls_outage")).isInstanceOf(DigiLockerOutcome.Unavailable.class);
		assertThat(session.isConsumed()).isFalse();
	}

	@Test
	void someoneUnder18IsRefusedAndRecorded() {
		DigiLockerSession session = openSession("dls_young");
		when(provider.fetch(any(), any())).thenReturn(new DigiLockerProvider.Fetch.Done(new DigiLockerProvider.Identity(
				"YOUNG SAMPLE", LocalDate.of(2009, 1, 1), "1234", null)));

		assertThat(service.completeDigiLocker(APPLICATION, "dls_young")).isEqualTo(new DigiLockerOutcome.Failed("UNDER_AGE"));
		assertThat(session.isConsumed()).isTrue();
		ArgumentCaptor<Verification> saved = ArgumentCaptor.forClass(Verification.class);
		verify(verifications).save(saved.capture());
		assertThat(saved.getValue().getStatus()).isEqualTo(EvidenceStatus.VERIFICATION_FAILED);
	}

	private DigiLockerSession openSession(String state) {
		DigiLockerSession session = new DigiLockerSession(APPLICATION, "DLREF", "ref-1", Tokens.sha256(state),
				NOW.plusSeconds(600));
		when(sessions.lockByStateHash(any())).thenReturn(Optional.of(session));
		return session;
	}
}
