package in.eko.eloka.partner.integration;

/** Compares two Indian names (initials, spelling variants, order) (EPS Name Match API). */
public interface NameMatcher {

	/**
	 * @return 0 (no match) to 1 (the same name)
	 * @throws ProviderNotConfiguredException when no provider is configured
	 * @throws ProviderUnavailableException when the provider cannot answer now
	 */
	double score(String name1, String name2);
}
