package in.eko.eloka.partner.integration.internal;

import java.util.List;
import in.eko.eloka.partner.integration.FaceCheck;

/** No face check configured: photos are taken without one (staff still look at every photo). */
class UnconfiguredFaceCheck implements FaceCheck {

	@Override
	public boolean enabled() {
		return false;
	}

	@Override
	public Result analyse(byte[] picture) {
		return new Result(List.of(), Eyes.UNKNOWN);
	}
}
