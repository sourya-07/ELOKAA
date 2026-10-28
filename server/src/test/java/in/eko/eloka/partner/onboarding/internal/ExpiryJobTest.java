package in.eko.eloka.partner.onboarding.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** One application that cannot be expired must not stop the job for the others. */
class ExpiryJobTest {

	@Test
	void keepsGoingAfterAFailure() {
		ApplicationService applications = mock(ApplicationService.class);
		UUID broken = UUID.randomUUID();
		UUID fine = UUID.randomUUID();
		when(applications.idleApplicationIds()).thenReturn(List.of(broken, fine));
		when(applications.expireIfIdle(broken)).thenThrow(new IllegalStateException("listener failed"));
		when(applications.expireIfIdle(fine)).thenReturn(true);

		new ExpiryJob(applications).run();

		verify(applications).expireIfIdle(fine);
	}
}
