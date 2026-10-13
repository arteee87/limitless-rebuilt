package limitless_rebuilt.config;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thrown when {@code config/limitless.json} exists but cannot be parsed.
 *
 * <p>Kept separate from the plain {@link java.io.IOException} that a missing or unreadable file
 * produces, so that the caller can tell the two apart: an I/O failure is worth propagating, while a
 * document the mod simply does not understand is handled by falling back to the defaults.
 *
 * <p>The file is never rewritten when this is thrown. An earlier port fell back to the defaults and
 * wrote them over the file, which meant that a single typo anywhere destroyed the whole
 * configuration on the next launch — and the log said only "Created default configuration", which
 * reads like a normal first run rather than like data loss.
 */
public final class ConfigReadException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * The two pieces of position Gson reports, e.g. {@code "at line 12 column 5"} and
	 * {@code "path $.anvil.levelLimit"}.
	 *
	 * <p>The Gson build this project resolves against has no {@code getLocation()} on
	 * {@code JsonParseException} and no {@code getLineNumber()} on {@code MalformedJsonException}, so
	 * the position has to be recovered from the message text. Gson appends it whenever it knows it;
	 * a document that fails before its first token is recognised is the one case it omits, and then
	 * there is no position to report.
	 */
	private static final Pattern POSITION = Pattern.compile("at line (?<line>\\d+) column (?<column>\\d+)");
	private static final Pattern PATH = Pattern.compile("path (?<path>\\S+)");

	/**
	 * The {@code java.lang.IllegalStateException:} or {@code ...MalformedJsonException:} prefix
	 * Gson wraps its own complaint in; it says nothing about the configuration.
	 */
	private static final Pattern CAUSE_PREFIX =
			Pattern.compile("^[A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*:\\s*");

	/** The documentation link Gson appends to every message. */
	private static final Pattern TROUBLESHOOTING =
			Pattern.compile("\\s*See https://github\\.com/google/gson.*", Pattern.DOTALL);

	private final String reason;
	private final int line;
	private final int column;
	private final String option;

	public ConfigReadException(Path file, RuntimeException cause) {
		this(file, cause, parse(cause));
	}

	private ConfigReadException(Path file, RuntimeException cause, Parsed parsed) {
		super(describe(file, parsed), cause);
		this.reason = parsed.reason();
		this.line = parsed.line();
		this.column = parsed.column();
		this.option = parsed.option();
	}

	private static Parsed parse(RuntimeException cause) {
		String text = cause.getMessage() == null ? cause.toString() : cause.getMessage();

		int marker = text.indexOf(" at line ");

		// everything before the position is the useful half; the position itself is lifted out of
		// it and re-emitted by describe() in this project's own layout
		String head = TROUBLESHOOTING.matcher(marker < 0 ? text : text.substring(0, marker))
				.replaceAll("")
				.trim();
		head = CAUSE_PREFIX.matcher(head).replaceFirst("").trim();

		int line = -1;
		int column = -1;
		Matcher position = POSITION.matcher(text);

		if (position.find()) {
			line = Integer.parseInt(position.group("line"));
			column = Integer.parseInt(position.group("column"));
		}

		// "$.enchantment.enchantingBlocks.whitelist[0]" names the option plus the array index that
		// offended; neither the JSON root nor the index belongs in the option name
		Matcher path = PATH.matcher(text);
		String option = path.find()
				? path.group("path").replaceFirst("^\\$\\.?", "").replaceFirst("\\[\\d+]$", "")
				: "";

		return new Parsed(head.isEmpty() ? cause.toString() : head, line, column, option);
	}

	private static String describe(Path file, Parsed parsed) {
		StringBuilder builder = new StringBuilder("Failed to read \"%s\": %s".formatted(file, parsed.reason()));

		if (parsed.line() > 0) {
			builder.append(" at line=").append(parsed.line()).append(" column=").append(parsed.column());
		}

		if (!parsed.option().isEmpty()) {
			builder.append(" option=").append(parsed.option());
		}

		return builder.append(" -> the file was left untouched, default values are loaded").toString();
	}

	/** The option Gson named, without the JSON root; empty when it could not tell. */
	public String option() {
		return option;
	}

	private record Parsed(String reason, int line, int column, String option) {
	}
}