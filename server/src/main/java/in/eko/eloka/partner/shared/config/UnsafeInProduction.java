package in.eko.eloka.partner.shared.config;

/**
 * Marker for beans that exist only to make local work easy (fixed OTP, seed data, dev
 * endpoints). {@link ProductionSafetyGuard} refuses to start production if any is present.
 */
public interface UnsafeInProduction {

	/** One line saying what is unsafe, shown in the startup error. */
	String unsafeReason();
}
