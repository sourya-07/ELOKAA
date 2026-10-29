// Copies MediaPipe's WebAssembly files from node_modules into public/, so the live-photo blink check is
// served from this site (the CSP allows only 'self'). Runs before `dev` and `build`; the copies are not
// committed (.gitignore). The face model (public/mediapipe/face_landmarker.task) is committed.
import { cpSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const from = join(root, "node_modules", "@mediapipe", "tasks-vision", "wasm");
const to = join(root, "public", "mediapipe", "wasm");
mkdirSync(to, { recursive: true });
for (const name of ["vision_wasm_internal.js", "vision_wasm_internal.wasm", "vision_wasm_nosimd_internal.js", "vision_wasm_nosimd_internal.wasm"]) {
	cpSync(join(from, name), join(to, name));
}
