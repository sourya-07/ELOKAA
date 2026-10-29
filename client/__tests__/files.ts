/** Test files whose bytes look like the real thing (the checks read the bytes, not the name or type). */

function bytesOf(parts: (number[] | string | number)[]): Uint8Array<ArrayBuffer> {
	const pieces = parts.map((part) =>
		typeof part === "string"
			? Uint8Array.from(Array.from(part, (c) => c.charCodeAt(0)))
			: typeof part === "number"
				? new Uint8Array(part).fill(0x41) // filler
				: Uint8Array.from(part),
	);
	const out = new Uint8Array(new ArrayBuffer(pieces.reduce((total, piece) => total + piece.length, 0)));
	let at = 0;
	for (const piece of pieces) {
		out.set(piece, at);
		at += piece.length;
	}
	return out;
}

/** A JPEG of about {@code size} bytes: header, start of scan, data, end marker (unless cut off). */
export function jpegFile(size: number, { cut = false, name = "photo.jpg" } = {}): File {
	const head = [0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, ...new Array(14).fill(0), 0xff, 0xda, 0x00, 0x08, 0, 0, 0, 0, 0, 0];
	const body = bytesOf([head, Math.max(0, size - head.length - 2), cut ? [] : [0xff, 0xd9]]);
	return new File([body], name, { type: "image/jpeg" });
}

export function pngFile(size: number, { cut = false, name = "scan.png" } = {}): File {
	const head = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
	return new File([bytesOf([head, Math.max(0, size - head.length - 8), cut ? [] : "IEND", cut ? [] : [0, 0, 0, 0]])], name, { type: "image/png" });
}

export function pdfFile(size: number, { cut = false, name = "scan.pdf" } = {}): File {
	const head = "%PDF-1.4\n1 0 obj\n";
	const tail = "\n%%EOF";
	return new File([bytesOf([head, Math.max(0, size - head.length - tail.length), cut ? "" : tail])], name, { type: "application/pdf" });
}

/** A file whose browser type and name say JPEG but whose bytes are not a picture. */
export function fakeJpeg(size: number): File {
	return new File([bytesOf(["MZ", size])], "photo.jpg", { type: "image/jpeg" });
}
