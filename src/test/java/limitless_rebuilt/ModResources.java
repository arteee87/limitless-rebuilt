package limitless_rebuilt;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;

/**
 * The translation file, found through the mod id instead of through a path written out by hand.
 *
 * <p>{@code assets/<namespace>} follows the id in {@code fabric.mod.json}, so a hardcoded path goes
 * stale the moment the id is renamed and every test that reads translations fails with "not on the
 * test classpath" — which is a true report about the path, but says nothing about the translations
 * themselves. Deriving it also turns the id into something the tests actually depend on: moving the
 * folder without moving the id is now a failure rather than a silently empty menu.
 */
public final class ModResources {
	private static final Pattern JSON_STRING = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");

	private ModResources() {
	}

	/** The mod id, read out of the manifest that Fabric itself reads. */
	public static String id() {
		try (InputStream stream = open("/fabric.mod.json")) {
			assertNotNull(stream, "fabric.mod.json is not on the test classpath");
			Matcher matcher = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"").matcher(read(stream));

			Assertions.assertTrue(matcher.find(), "fabric.mod.json has no id");

			return matcher.group(1);
		} catch (IOException e) {
			throw new AssertionError("could not read fabric.mod.json", e);
		}
	}

	/** Every translation in the shipped en_us.json, in file order. */
	public static Map<String, String> lang() {
		String path = "/assets/" + id() + "/lang/en_us.json";

		try (InputStream stream = open(path)) {
			assertNotNull(stream, path + " is not on the test classpath, but the id in fabric.mod.json "
					+ "points at it; the folder and the id have drifted apart");
			Map<String, String> values = new LinkedHashMap<>();
			Matcher matcher = JSON_STRING.matcher(read(stream));

			while (matcher.find()) {
				values.put(matcher.group(1), matcher.group(2));
			}

			return values;
		} catch (IOException e) {
			throw new AssertionError("could not read " + path, e);
		}
	}

	private static InputStream open(String path) {
		return ModResources.class.getResourceAsStream(path);
	}

	private static String read(InputStream stream) throws IOException {
		return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
	}
}