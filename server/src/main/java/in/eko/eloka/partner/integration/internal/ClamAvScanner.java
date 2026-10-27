package in.eko.eloka.partner.integration.internal;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import in.eko.eloka.partner.integration.MalwareScanner;
import in.eko.eloka.partner.integration.ProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * clamd over TCP with the INSTREAM command: {@code zINSTREAM\0}, then chunks prefixed with their
 * 4-byte length, then a zero length. clamd answers {@code stream: OK} or {@code stream: <name> FOUND}.
 * Anything else (errors, size limit, timeouts) refuses the file.
 */
class ClamAvScanner implements MalwareScanner {

	private static final Logger log = LoggerFactory.getLogger(ClamAvScanner.class);
	private static final int CHUNK = 64 * 1024;

	private final String host;
	private final int port;
	private final Duration timeout;

	ClamAvScanner(String host, int port, Duration timeout) {
		if (host == null || host.isBlank()) {
			throw new IllegalStateException("eloka.integration.clamav.host is not set");
		}
		this.host = host;
		this.port = port;
		this.timeout = timeout;
	}

	@Override
	public Verdict scan(byte[] bytes) {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(host, port), (int) timeout.toMillis());
			socket.setSoTimeout((int) timeout.toMillis());
			DataOutputStream out = new DataOutputStream(socket.getOutputStream());
			out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
			for (int offset = 0; offset < bytes.length; offset += CHUNK) {
				int length = Math.min(CHUNK, bytes.length - offset);
				out.writeInt(length);
				out.write(bytes, offset, length);
			}
			out.writeInt(0);
			out.flush();
			String answer = readAnswer(socket.getInputStream());
			if (answer.endsWith("OK")) {
				return Verdict.CLEAN;
			}
			if (answer.endsWith("FOUND")) {
				log.warn("Malware scanner flagged an upload");
				return Verdict.INFECTED;
			}
			log.warn("Malware scanner answered unexpectedly: {}", answer);
			throw new ProviderUnavailableException("File checking");
		}
		catch (IOException ex) {
			log.warn("Malware scanner unreachable: {}", ex.getClass().getSimpleName());
			throw new ProviderUnavailableException("File checking");
		}
	}

	private static String readAnswer(InputStream in) throws IOException {
		byte[] answer = in.readNBytes(1024);
		return new String(answer, StandardCharsets.US_ASCII).replace("\0", "").trim();
	}
}
