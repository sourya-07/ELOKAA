package in.eko.eloka.partner.integration.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import in.eko.eloka.partner.integration.FaceCheck;
import in.eko.eloka.partner.integration.ProviderUnavailableException;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.FaceDetectorYN;

/**
 * OpenCV on our server: the YuNet face detector, and an eye detector (works with glasses) on the upper
 * part of the largest face. Pictures are scaled to at most 640 px for speed. The detectors are not
 * thread-safe, so one picture is checked at a time (photos are rare). Models: resources/face/README.md.
 */
class OpenCvFaceCheck implements FaceCheck {

	private static final int MAX_SIDE = 640;
	private static final float MIN_SCORE = 0.8f;

	private final FaceDetectorYN faces;
	private final CascadeClassifier eyes;

	OpenCvFaceCheck() {
		nu.pattern.OpenCV.loadLocally();
		Path model = copy("face/face_detection_yunet_2023mar.onnx", ".onnx");
		Path eyeModel = copy("face/haarcascade_eye_tree_eyeglasses.xml", ".xml");
		this.faces = FaceDetectorYN.create(model.toString(), "", new Size(320, 320), MIN_SCORE, 0.3f, 50);
		this.eyes = new CascadeClassifier(eyeModel.toString());
		if (eyes.empty()) {
			throw new IllegalStateException("The eye detector model could not be loaded");
		}
	}

	@Override
	public boolean enabled() {
		return true;
	}

	@Override
	public synchronized Result analyse(byte[] picture) {
		try {
			Mat image = Imgcodecs.imdecode(new MatOfByte(picture), Imgcodecs.IMREAD_COLOR); // turned upright by EXIF
			if (image.empty()) {
				return new Result(List.of(), Eyes.UNKNOWN);
			}
			double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(image.cols(), image.rows()));
			if (scale < 1.0) {
				Imgproc.resize(image, image, new Size(Math.round(image.cols() * scale), Math.round(image.rows() * scale)));
			}
			faces.setInputSize(new Size(image.cols(), image.rows()));
			Mat found = new Mat();
			faces.detect(image, found);
			List<Rect> boxes = new ArrayList<>();
			for (int row = 0; row < found.rows(); row++) {
				boxes.add(new Rect((int) found.get(row, 0)[0], (int) found.get(row, 1)[0], (int) found.get(row, 2)[0],
						(int) found.get(row, 3)[0]));
			}
			boxes.sort(Comparator.comparingInt((Rect box) -> box.width).reversed());
			List<Face> result = boxes.stream()
				.map(box -> new Face((box.x + box.width / 2.0) / image.cols(), (box.y + box.height / 2.0) / image.rows(),
						(double) box.width / image.cols()))
				.toList();
			return new Result(result, boxes.isEmpty() ? Eyes.UNKNOWN : eyesOf(image, boxes.getFirst()));
		}
		catch (RuntimeException ex) {
			throw new ProviderUnavailableException("Face check");
		}
	}

	/** Open when the eye detector finds an eye in the upper part of the face; it rarely finds closed ones. */
	private Eyes eyesOf(Mat image, Rect face) {
		int x = Math.max(0, face.x);
		int y = Math.max(0, face.y);
		int width = Math.min(face.width, image.cols() - x);
		int height = Math.min((int) (face.height * 0.6), image.rows() - y);
		if (width < 20 || height < 20) {
			return Eyes.UNKNOWN;
		}
		Mat grey = new Mat();
		Imgproc.cvtColor(image.submat(new Rect(x, y, width, height)), grey, Imgproc.COLOR_BGR2GRAY);
		Imgproc.equalizeHist(grey, grey);
		MatOfRect found = new MatOfRect();
		int min = Math.max(8, width / 8);
		eyes.detectMultiScale(grey, found, 1.1, 3, 0, new Size(min, min), new Size());
		return found.toArray().length > 0 ? Eyes.OPEN : Eyes.CLOSED;
	}

	private static Path copy(String resource, String suffix) {
		try (InputStream in = OpenCvFaceCheck.class.getClassLoader().getResourceAsStream(resource)) {
			if (in == null) {
				throw new IllegalStateException("Missing face model " + resource);
			}
			Path file = Files.createTempFile("eloka-face", suffix);
			Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
			file.toFile().deleteOnExit();
			return file;
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not prepare face model " + resource, ex);
		}
	}
}
