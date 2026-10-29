import { Select } from "@chakra-ui/react";
import { isAvailable } from "@/i18n/catalogs";
import { useI18n } from "@/i18n/I18nProvider";
import { isLocale, LOCALES } from "@/i18n/locales";

/**
 * The language list, always the same seven in the same order. A language without a complete, reviewed
 * translation is listed but cannot be chosen ("not available yet"), so nothing claims a translation
 * that does not exist.
 */
export function LanguageSelector() {
	const { locale, setLocale, t } = useI18n();
	return (
		<Select
			aria-label={t("language.label")}
			size="sm"
			width="auto"
			borderRadius="control"
			value={locale}
			onChange={(event) => {
				if (isLocale(event.target.value) && isAvailable(event.target.value)) {
					setLocale(event.target.value);
				}
			}}
		>
			{LOCALES.map((option) => {
				const available = isAvailable(option.code);
				return (
					<option key={option.code} value={option.code} lang={option.code} disabled={!available}>
						{available ? option.label : `${option.label}: ${t("language.unavailable")}`}
					</option>
				);
			})}
		</Select>
	);
}
