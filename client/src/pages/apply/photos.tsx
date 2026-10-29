import { Button, Flex, Stack, Text } from "@chakra-ui/react";
import { ApplyShell, Section } from "@/components/ApplyShell";
import { CameraCapture } from "@/components/CameraCapture";
import { ErrorBanner } from "@/components/ErrorBanner";
import { FileSlot } from "@/components/FileSlot";
import { StatusPill } from "@/components/StatusPill";
import { useApplication, useStepGuard } from "@/features/apply/ApplicationContext";
import { canOpen } from "@/features/apply/guards";
import { SubmitStep } from "@/features/apply/SubmitStep";
import { useDocuments } from "@/features/apply/useDocuments";
import { GOOD_ACCURACY_M, type LocationState, MAX_ACCURACY_M, useGeolocation } from "@/features/apply/useGeolocation";
import { withApplication } from "@/features/apply/withApplication";
import { PHOTO } from "@/lib/validation";

const LOCATION_HELP: Partial<Record<LocationState["kind"], string>> = {
	denied: "Location is blocked for this site. Allow location in your browser's site settings (the icon next to the address), then try again.",
	unavailable: "Your device could not find its location. Turn on location (GPS) in your phone's settings, then try again.",
	timeout: "Finding your location took too long. Move near a window or outside, then try again.",
	unsupported: "This browser cannot share your location. Use an up-to-date Chrome, Safari or Firefox.",
};

/** One line saying where the location stands, for the live photo. */
function LocationLine({ state, onRetry }: { state: LocationState; onRetry: () => void }) {
	if (state.kind === "idle") {
		return null;
	}
	if (state.kind === "locating") {
		return (
			<Text role="status" fontSize="sm" color="text.muted">
				Finding your location…
			</Text>
		);
	}
	if (state.kind === "ready") {
		const metres = Math.round(state.fix.accuracy);
		if (state.fix.accuracy > MAX_ACCURACY_M) {
			return <ErrorBanner tone="warning" message={`Your location is too rough (about ${metres} m). Turn on precise location or GPS; it updates by itself.`} />;
		}
		return (
			<Text role="status" fontSize="sm" color={state.fix.accuracy > GOOD_ACCURACY_M ? "status.warn.fg" : "status.ok.fg"}>
				{state.fix.accuracy > GOOD_ACCURACY_M
					? `Location found, approximate (about ${metres} m). Precise location or GPS makes it better.`
					: `Location found (within about ${metres} m).`}
			</Text>
		);
	}
	return (
		<Stack spacing={2}>
			<ErrorBanner tone="warning" message={LOCATION_HELP[state.kind] ?? null} />
			{state.kind !== "unsupported" && (
				<Button size="sm" variant="outline" alignSelf="flex-start" onClick={onRetry}>
					Try location again
				</Button>
			)}
		</Stack>
	);
}

/**
 * The two photos, clearly apart: a live photo taken now with the camera, stamped with where it was
 * taken (location is required; it is never invented), and a passport-size photo uploaded as a file.
 */
function PhotosPage() {
	const { snapshot } = useApplication();
	useStepGuard((s) => canOpen(s, "PHOTOS"));
	const { documents, upload, contentUrl, ruleFor } = useDocuments();
	const location = useGeolocation();
	const live = documents.filter((d) => d.kind === "LIVE_PHOTO");
	const fresh = location.current();
	const fix = location.state.kind === "ready" && fresh && fresh.accuracy <= MAX_ACCURACY_M ? fresh : null;

	const sendLive = async (file: File, blinkFrame: Blob) => {
		const now = location.current();
		if (!now || now.accuracy > MAX_ACCURACY_M) {
			throw new Error("Your location is needed with the live photo. Allow location, then take the photo again.");
		}
		await upload(
			"LIVE_PHOTO",
			file,
			{
				latitude: String(now.latitude),
				longitude: String(now.longitude),
				accuracy_m: String(now.accuracy),
				captured_at: new Date(now.at).toISOString(),
			},
			undefined,
			undefined,
			{ blink_frame: blinkFrame }, // the eyes-closed moment, checked by the server and never stored
		);
		location.stop();
	};

	return (
		<ApplyShell title="Your photos" subtitle="A live photo taken now, and a passport-size photo." progress={snapshot}>
			<Section label="Live photo, taken now">
				<Text>
					Take a photo of yourself with this device&apos;s camera. We record where it is taken, so your browser will ask to share your location: please allow it.
				</Text>
				{live.map((file) => (
					<Flex key={file.document_id} justify="space-between" align="flex-start" gap={3} wrap="wrap">
						<Stack spacing={1} minW={0}>
							<Text>
								Photo taken{file.location ? ` · location within about ${file.location.accuracy_m} m` : ""}
							</Text>
							{file.location && (
								<>
									{/* Read-only: what the browser reported, never a field the applicant can edit */}
									<Text fontFamily="mono" fontSize="sm" translate="no">
										Latitude {file.location.latitude.toFixed(5)}, longitude {file.location.longitude.toFixed(5)}
									</Text>
									{file.location.approximate_address && <Text fontSize="sm">Approximate address: {file.location.approximate_address}</Text>}
									<Text fontSize="sm" color="text.muted">
										Reported by your browser at {new Date(file.location.captured_at).toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" })}. It is where
										your device said it was, not a checked address{file.location.approximate_address ? " (the address is looked up from it on OpenStreetMap)" : ""}.
									</Text>
								</>
							)}
						</Stack>
						<StatusPill status={file.status} forApplicant />
					</Flex>
				))}
				<CameraCapture
					onCapture={sendLive}
					// No fresh fix (none yet, too old, or the watch stopped after an upload): look again
					onStart={() => {
						if (!location.current()) {
							location.start();
						}
					}}
					canUse={!!fix}
					waitingFor={<LocationLine state={location.state} onRetry={location.start} />}
				/>
				{fix && <LocationLine state={location.state} onRetry={location.start} />}
			</Section>
			<Section label="Passport-size photo">
				<Text color="text.muted" fontSize="sm">
					A recent photo of your face on a plain light background, like the one on a passport. Upload it as a file.
				</Text>
				<FileSlot
					label="Passport-size photo"
					rule={ruleFor("PASSPORT_PHOTO", PHOTO)}
					required
					files={documents.filter((d) => d.kind === "PASSPORT_PHOTO")}
					onUpload={(file, onProgress, signal) => upload("PASSPORT_PHOTO", file, {}, onProgress, signal)}
					contentUrl={contentUrl}
				/>
			</Section>
			<SubmitStep path="/photos/submit" />
		</ApplyShell>
	);
}

export default withApplication(PhotosPage);
