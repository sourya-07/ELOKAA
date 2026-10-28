package in.eko.eloka.partner.onboarding.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Expires idle applications and overdue corrections, hourly. */
@Component
class ExpiryJob {

	private static final Logger log = LoggerFactory.getLogger(ExpiryJob.class);

	private final ApplicationService applications;

	ExpiryJob(ApplicationService applications) {
		this.applications = applications;
	}

	@Scheduled(initialDelayString = "${eloka.onboarding.expiry-initial-delay:PT10M}",
			fixedDelayString = "${eloka.onboarding.expiry-interval:PT1H}")
	void run() {
		int expired = 0;
		for (var id : applications.idleApplicationIds()) {
			// One bad row must not stop the others, or the job would stall on it every hour
			try {
				if (applications.expireIfIdle(id)) {
					expired++;
				}
			}
			catch (RuntimeException ex) {
				log.warn("Could not expire application {}", id, ex);
			}
		}
		if (expired > 0) {
			log.info("Expired {} idle application(s)", expired);
		}
	}

	@Configuration(proxyBeanMethods = false)
	@EnableScheduling
	static class SchedulingConfig {
	}
}
