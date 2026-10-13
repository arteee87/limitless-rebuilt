package limitless_rebuilt.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import limitless_rebuilt.LimitlessLogger;

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

	/**
	 * Reads the configuration, creating it when it is absent.
	 *
	 * <p>A file that exists but cannot be parsed is <b>never</b> rewritten. An earlier port fell back
	 * to the defaults and wrote them over the offending file, so a single typo anywhere destroyed the
	 * user's configuration on the next launch, and the log line said only "Created default
	 * configuration", which reads like an ordinary first run rather than like data loss. Such a file
	 * now raises {@link ConfigReadException} instead, and the caller decides what to show.
	 *
	 * <p>An <b>empty</b> file is the one exception to that rule: Gson reads it as {@code null}
	 * rather than as a failure, there is nothing in it to lose, and writing the defaults turns it
	 * into a usable template instead of leaving the user with a permanently broken configuration.
	 */
	public static Configuration read(Path file) throws IOException {
		if (!Files.exists(file)) {
			Configuration configuration = defaults();
			write(file, configuration);
			LimitlessLogger.info("Created default configuration at \"%s\".".formatted(file));
			return configuration;
		}

		Configuration configuration;

		try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			configuration = GSON.fromJson(reader, Configuration.class);
		} catch (RuntimeException e) {
			throw new ConfigReadException(file, e);
		}

		if (configuration == null) {
			LimitlessLogger.warn("Configuration file \"%s\" is empty, writing the defaults.".formatted(file));
			configuration = defaults();
			write(file, configuration);
			return configuration;
		}

		configuration.postLoad();
		LimitlessLogger.info("Loaded configuration from \"%s\".".formatted(file));
		return configuration;
	}

	/** A fully populated configuration carrying nothing but the shipped defaults. */
	public static Configuration defaults() {
		Configuration configuration = new Configuration();
		configuration.postLoad();
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