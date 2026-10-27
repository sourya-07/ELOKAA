package in.eko.eloka.partner.shared.config;

/** Where the app runs. Drives the production safety checks, never business rules. */
public enum ElokaEnvironment {
	LOCAL,
	TEST,
	STAGING,
	PRODUCTION;

	public boolean isProduction() {
		return this == PRODUCTION;
	}

	/** Development shortcuts (fakes, fixed OTP, published keys) are allowed only on laptops and in tests. */
	public boolean allowsDevelopmentShortcuts() {
		return this == LOCAL || this == TEST;
	}
}
