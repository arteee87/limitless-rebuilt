package limitless.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import limitless.LimitlessLogger;

/**
 * Reads and writes {@code config/limitless.json}.
 *
 * <p>The layout is the one used by limitless 2.0.0 (AutoConfig + Gson), so configuration files
 * written for 1.19.3 keep working: same file name, same field names, same nesting.
 */
public final class ConfigSerializer {
	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.create();

	private ConfigSerializer() {
	}

	public static Configuration read(Path file) throws IOException {
		if (Files.exists(file)) {
			try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				Configuration configuration = GSON.fromJson(reader, Configuration.class);

				if (configuration != null) {
					configuration.postLoad();
					return configuration;
				}

				LimitlessLogger.warn("Configuration file \"%s\" is empty.".formatted(file));
			} catch (RuntimeException e) {
				LimitlessLogger.error("Failed to read \"%s\": %s".formatted(file, e.toString()));
			}
		}

		Configuration configuration = new Configuration();
		configuration.postLoad();
		write(file, configuration);
		LimitlessLogger.info("Created default configuration at \"%s\".".formatted(file));
		return configuration;
	}

	public static void write(Path file, Configuration configuration) throws IOException {
		try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			writer.write(GSON.toJson(configuration));
			writer.write(System.lineSeparator());
		}
	}

	public static String toJson(Configuration configuration) {
		return GSON.toJson(configuration);
	}
}