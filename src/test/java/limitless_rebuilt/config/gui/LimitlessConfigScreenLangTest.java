package limitless_rebuilt.config.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import limitless_rebuilt.ModResources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every translation key the screen builds has to exist in {@code en_us.json}, and every key in that
 * file has to be one the screen builds.
 *
 * <p>A missing key fails nowhere: Minecraft draws the raw key instead, so the entry simply reads
 * {@code text.autoconfig.limitless-rebuilt.option.enchantment.tooltip} in the menu. That is what
 * happened — {@code section} built its title from {@code text.autoconfig.limitless-rebuilt.} while
 * every other entry used {@code text.autoconfig.limitless-rebuilt.option.}, and all eight sub
 * category titles came out untranslated. Nothing in the build noticed, because the keys are only ever
 * joined at runtime.
 *
 * <p>Keys are recovered from the source rather than listed, so an option added, renamed or dropped
 * later is covered without touching this test.
 */
class LimitlessConfigScreenLangTest {
	private static final Path SOURCE = Path.of("src/main/java/limitless_rebuilt/config/gui/LimitlessConfigScreen.java");

	/**
	 * The two namespaces the mod puts its own keys under, both derived from the id rather than written
	 * out: {@code assets/<id>} and every {@code <prefix>.<id>} key move together with it.
	 */
	private static final String OPTION = "text.autoconfig." + ModResources.id() + ".option.";
	private static final String CONFIG = "config." + ModResources.id() + ".";

	/** The helpers that take a bare key and join a namespace onto it. */
	private static final List<String> KEY_HELPERS = List.of("toggle", "field", "section");

	/** 1.21.11 registers forty three enchantments and the screen shows a row each. */
	private static final int SHIPPED_ENCHANTMENTS = 43;

	@Test
	@DisplayName("every key the screen builds is translated")
	void everyKeyIsTranslated() throws IOException {
		Set<String> keys = screenKeys();
		Map<String, String> lang = lang();
		TreeSet<String> missing = new TreeSet<>();

		for (String key : keys) {
			if (!lang.containsKey(key)) {
				missing.add(key);
			}
		}

		assertEquals(new TreeSet<String>(), missing,
				"the menu would show these raw keys; run /limitless debug to compare:\n" + missing);
	}

	/** A translation nothing points at is a removed option whose string was left behind. */
	@Test
	@DisplayName("no translation is left behind")
	void noStaleKeys() throws IOException {
		Map<String, String> lang = lang();
		Set<String> used = screenKeys();
		TreeSet<String> stale = new TreeSet<>();

		for (String key : lang.keySet()) {
			if ((key.startsWith(OPTION) || key.startsWith(CONFIG)) && !used.contains(key)) {
				stale.add(key);
			}
		}

		assertEquals(new TreeSet<String>(), stale, "nothing reads these any more:\n" + stale);
	}

	/**
	 * The per enchantment rows are keyed by registry identifier at runtime, so their set cannot be
	 * read out of the source: {@code EnchantmentConfiguration#postLoad} adds whatever the registry
	 * holds. The count is the guard — a new enchantment in a future version then shows up as an
	 * untranslated row instead of a raw {@code config.limitless-rebuilt.<id>} in the menu.
	 */
	@Test
	@DisplayName("every enchantment name is spelled out")
	void enchantmentNamesAreSpelledOut() throws IOException {
		TreeSet<String> names = new TreeSet<>();

		for (String key : lang().keySet()) {
			if (key.startsWith(CONFIG + "minecraft:")) {
				names.add(key);
			}
		}

		assertEquals(SHIPPED_ENCHANTMENTS, names.size(),
				"the per enchantment names changed; add one for every new enchantment:\n" + names);
	}

	/** Every key the screen can build, including the ones it assembles from parts. */
	private static Set<String> screenKeys() throws IOException {
		String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
		Set<String> keys = new LinkedHashSet<>();

		for (String helper : KEY_HELPERS) {
			for (String literal : callLiterals(source, helper)) {
				add(keys, literal);
			}
		}

		// a radius section is titled by its base and then expands into four bounds
		for (String literal : callLiterals(source, "radiusSection")) {
			String base = literal.startsWith("text.") ? literal : OPTION + literal;
			add(keys, base);

			for (String axis : new String[]{"xz", "y"}) {
				for (String bound : new String[]{"min", "max"}) {
					keys.add(base + "." + axis + "." + bound);
				}
			}
		}

		// the per enchantment rows are built onto a namespace the screen derives from the mod id, so their
		// literals sit under config.<id> rather than under the option namespace
		for (String literal : concatenatedLiterals(source, "namespace")) {
			add(keys, CONFIG + literal);
		}

		// Text.translatable takes the finished key otherwise
		for (String literal : callLiterals(source, "Text.translatable")) {
			if (!isConcatenated(source, literal, "namespace")) {
				add(keys, literal);
			}
		}

		// and translationKey is the fallback for a key that is already complete
		Matcher fallback = Pattern.compile("translationKey\\(\"([^\"]+)\"\\)").matcher(source);

		while (fallback.find()) {
			keys.add(fallback.group(1));
		}

		// per enchantment rows are looked up by identifier, so every one of them counts
		for (String key : lang().keySet()) {
			if (key.startsWith(CONFIG + "minecraft:")) {
				keys.add(key);
			}
		}

		return keys;
	}

	/**
	 * One literal as a key. A leading dot means the screen appends the fragment to a key it already
	 * has ({@code key + ".xz.min"}) and a trailing dot means the literal is a namespace concatenated
	 * onto one; neither is a key on its own.
	 */
	private static void add(Set<String> keys, String literal) {
		if (literal.isEmpty() || literal.startsWith(".") || literal.endsWith(".")) {
			return;
		}

		keys.add(literal.startsWith("text.") || literal.startsWith(CONFIG) ? literal : OPTION + literal);
	}

	/**
	 * Every string literal passed to {@code name(...)}. Reading the whole call rather than just its
	 * first argument is what catches the description keys {@code toggle} takes as a later argument,
	 * and the radius base, which is its third.
	 */
	/** Every literal that appears as {@code <variable> + "..."} anywhere in the file. */
	private static List<String> concatenatedLiterals(String source, String variable) {
		List<String> found = new ArrayList<>();
		Matcher matcher = Pattern.compile(Pattern.quote(variable) + "\\s*\\+\\s*\"([^\"]+)\"").matcher(source);

		while (matcher.find()) {
			found.add(matcher.group(1));
		}

		return found;
	}

	/** Whether this literal is the tail of {@code <variable> + "..."} rather than a key in itself. */
	private static boolean isConcatenated(String source, String literal, String variable) {
		return source.contains(variable + " + \"" + literal + "\"");
	}

	private static List<String> callLiterals(String source, String name) {
		List<String> found = new ArrayList<>();

		Matcher calls = Pattern.compile("\\b" + Pattern.quote(name) + "\\(").matcher(source);

		while (calls.find()) {
			int depth = 1;
			int at = calls.end();

			while (depth > 0 && at < source.length()) {
				char current = source.charAt(at);

				if (current == '(') {
					depth++;
				} else if (current == ')') {
					depth--;
				}

				at++;
			}

			Matcher literal = Pattern.compile("\"([^\"]+)\"").matcher(source.substring(calls.end(), at - 1));

			while (literal.find()) {
				found.add(literal.group(1));
			}
		}

		return found;
	}

	private static Map<String, String> lang() {
		return ModResources.lang();
	}
}