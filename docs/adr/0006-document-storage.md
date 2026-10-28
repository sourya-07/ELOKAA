# ADR-0006: Encrypted private file storage behind a port; images re-encoded

- **Status:** accepted
- **Date:** 2026-10-27
- **Deciders:** project owner

## Context

Applicants upload a live photo, a passport-size photo, masked Aadhaar images, certificates and voter ID or driving licence images. The hosting target (and so object storage) is not chosen yet.

## Decision

- File bytes never go into MySQL. A `DocumentStorage` port stores them; the first adapter (ported from eloka) writes **AES-256-GCM encrypted** files to a private volume. An S3-compatible adapter is added once hosting is decided.
- MySQL keeps metadata: type, size, SHA-256, detected format, dimensions, scan status, review status.
- The file type is decided from the bytes, not the name or browser MIME type. Photos accept JPEG only. Certificates and ID images accept JPEG or PDF.
- Images are decoded and **re-encoded as JPEG with metadata (EXIF, location) removed**, which also defeats polyglot files. The EXIF orientation is applied to the pixels first, so phone photos are not stored sideways. The live photo's location comes from the browser at capture and is stored apart, encrypted (`photo_location`, journey v2), never read from EXIF. PDFs go through `MalwareScanner` before they count as submitted.
- Files are never public. They are streamed only to the owner or authorised staff, with `Cache-Control: no-store`.

## Consequences

- Backups must include the file volume and the encryption keys separately.
- A ClamAV container is needed in staging and production (about 1 GB RAM).
