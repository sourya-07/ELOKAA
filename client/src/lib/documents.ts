/** What each document kind is called for the applicant (server kinds, docs/API.md). */
export const DOCUMENT_LABEL: Record<string, string> = {
	LIVE_PHOTO: "live photo",
	PASSPORT_PHOTO: "passport-size photo",
	AADHAAR_FRONT: "Aadhaar front",
	AADHAAR_BACK: "Aadhaar back",
	PAN_CARD: "PAN card",
	VOTER_ID_FRONT: "voter ID front",
	VOTER_ID_BACK: "voter ID back",
	DRIVING_LICENCE_FRONT: "driving licence front",
	DRIVING_LICENCE_BACK: "driving licence back",
	CLASS_10_MARKSHEET: "class 10 marksheet",
	CLASS_12_MARKSHEET: "class 12 marksheet",
	GRADUATION_CERTIFICATE: "graduation certificate",
	DIPLOMA_CERTIFICATE: "diploma certificate",
	COMPUTER_CERTIFICATE: "computer certificate",
};

/** "Add your Aadhaar back, PAN card." from the server's missing kinds. */
export function missingText(kinds: string[]): string {
	return `Add your ${kinds.map((kind) => DOCUMENT_LABEL[kind] ?? kind.toLowerCase().replace(/_/g, " ")).join(", ")}.`;
}
