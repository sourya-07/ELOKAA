import type { NextApiRequest, NextApiResponse } from "next";

/** Liveness check for the container and the TLS edge. No backend call, no data. */
export default function handler(_req: NextApiRequest, res: NextApiResponse) {
	res.setHeader("Cache-Control", "no-store");
	res.status(200).json({ status: "UP" });
}
