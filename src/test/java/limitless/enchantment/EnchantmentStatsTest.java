package limitless.enchantment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class EnchantmentStatsTest {
	@SuppressWarnings("unchecked")
	private static Map<String, Object> categories() throws Exception {
		Field field = EnchantmentStats.class.getDeclaredField("CATEGORIES");
		field.setAccessible(true);
		return (Map<String, Object>) field.get(null);
	}

	/**
	 * The channel groups sharpness, smite, bane of arthropods and impaling together, yet they answer
	 * to different targets and therefore need their own rows. A missing entry would silently drop the
	 * line rather than fail.
	 */
	@ParameterizedTest
	@CsvSource({
			"minecraft:smite, DAMAGE",
			"minecraft:bane_of_arthropods, DAMAGE",
			"minecraft:impaling, DAMAGE",
			"minecraft:power, DAMAGE",
			"minecraft:knockback, KNOCKBACK",
			"minecraft:punch, KNOCKBACK",
			"minecraft:protection, PROTECTION",
			"minecraft:fire_protection, PROTECTION",
			"minecraft:blast_protection, PROTECTION",
			"minecraft:projectile_protection, PROTECTION",
			"minecraft:feather_falling, PROTECTION",
	})
	@DisplayName("every enchantment that deserves a line has a category")
	void mappedEnchantments(String id, EnchantmentStats.Channel expected) throws Exception {
		Object category = categories().get(id);
		assertTrue(category != null, id + " has no category");

		Field channel = category.getClass().getDeclaredField("channel");
		channel.setAccessible(true);
		assertEquals(expected, channel.get(category));
	}

	@Test
	@DisplayName("sharpness has no line of its own")
	void sharpnessIsFoldedIntoAttackDamage() throws Exception {
		assertEquals(null, categories().get("minecraft:sharpness"));
	}

	/** The 24 enchantments deliberately left out, checked so none creeps back by accident. */
	@ParameterizedTest
	@ValueSource(strings = {
			"minecraft:binding_curse", "minecraft:vanishing_curse", "minecraft:looting",
			"minecraft:luck_of_the_sea", "minecraft:lure", "minecraft:loyalty", "minecraft:riptide",
			"minecraft:quick_charge", "minecraft:density", "minecraft:silk_touch", "minecraft:fortune",
			"minecraft:infinity", "minecraft:unbreaking", "minecraft:mending", "minecraft:channeling",
			"minecraft:flame", "minecraft:fire_aspect", "minecraft:frost_walker", "minecraft:wind_burst",
			"minecraft:thorns", "minecraft:multishot", "minecraft:piercing", "minecraft:breach",
			"minecraft:soul_speed",
	})
	@DisplayName("the hidden enchantments have no line")
	void hiddenEnchantments(String id) throws Exception {
		assertEquals(null, categories().get(id));
	}

	/**
	 * There is no attribute channel left. Vanilla prints those bonuses itself, with the value and the
	 * unit, so a second copy was a duplicate that also lost the number. The constant staying absent is
	 * the regression guard: it would have to be reintroduced, along with the toggle, to bring the
	 * duplication back.
	 */
	@Test
	@DisplayName("nothing is read off attribute_effects any more")
	void attributeChannelIsGone() throws Exception {
		for (String name : java.util.List.of("ATTRIBUTE_LINE", "HIDDEN_ATTRIBUTES")) {
			assertTrue(java.util.Arrays.stream(EnchantmentStats.class.getDeclaredFields())
					.noneMatch(field -> field.getName().equals(name)), name + " is back");
		}

		assertEquals(3, EnchantmentStats.Channel.values().length, "a channel was added back");
	}

	@ParameterizedTest
	@CsvSource({"0, 0", "3, 3", "3.5, 3.5", "-2, -2", "-2.5, -2.5", "499.5, 499.5"})
	@DisplayName("values are printed without an exponent")
	void formatting(double value, String expected) {
		assertEquals(expected, EnchantmentStats.format((float) value));
	}

	/** The configured level ceiling is Integer.MAX_VALUE, so the sum can leave float range. */
	@Test
	@DisplayName("very large bonuses stay plain decimals")
	void hugeFormatting() {
		String formatted = EnchantmentStats.format(1.073741824E9F);
		assertTrue(formatted.matches("-?\\d+(\\.\\d+)?"), formatted);
		assertTrue(!formatted.contains("E") && !formatted.contains("e"), formatted);
	}

	/**
	 * The fault that shipped once: a line keyed on a translation that cannot hold the number has it
	 * accepted and silently dropped, and nothing is raised — not by the compiler, not by Mixin, not
	 * by the checker, because the damage only lands while the line is being drawn. So every key the
	 * collector can emit is checked against the file that renders it, and the number of
	 * substitutions has to match the single argument the mixin passes.
	 */
	@Test
	@DisplayName("every line resolves to a translation that takes one number")
	void translationsCanHoldTheirArgument() throws Exception {
		Map<String, String> lang = lang();

		for (String key : lineKeys()) {
			String value = lang.get(key);

			assertNotNull(value, key + " is emitted by EnchantmentStats but missing from en_us.json");
			assertEquals(1, count(value, "%s"), key + " must take exactly one argument");
		}
	}

	/** Punch and knockback share one row, so the categories cannot both be mapped to it separately. */
	@Test
	@DisplayName("the two knockback enchantments are mapped to the same line")
	void knockbackEnchantmentsShareALine() throws Exception {
		Object knockback = categories().get("minecraft:knockback");
		Object punch = categories().get("minecraft:punch");

		assertEquals(key(knockback), key(punch));
	}

	/** Protection and feather falling answer to different rows, so they must not be folded together. */
	@Test
	@DisplayName("protection and feather falling stay on separate lines")
	void protectionLinesDiffer() throws Exception {
		assertNotEquals(key(categories().get("minecraft:protection")),
				key(categories().get("minecraft:feather_falling")));
	}

	private static String key(Object category) throws Exception {
		Field field = category.getClass().getDeclaredField("key");
		field.setAccessible(true);
		return (String) field.get(category);
	}

	/** Every key {@code collect} can produce: one per category. */
	private static Set<String> lineKeys() throws Exception {
		Set<String> keys = new LinkedHashSet<>();

		for (Object category : categories().values()) {
			keys.add(key(category));
		}

		return keys;
	}

	private static Map<String, String> lang() throws IOException {
		Map<String, String> values = new LinkedHashMap<>();
		String text;

		try (InputStream stream = EnchantmentStatsTest.class
				.getResourceAsStream("/assets/limitless/lang/en_us.json")) {
			assertNotNull(stream, "en_us.json is not on the test classpath");
			text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}

		Matcher matcher = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"").matcher(text);

		while (matcher.find()) {
			values.put(matcher.group(1), matcher.group(2));
		}

		return values;
	}

	private static int count(String value, String needle) {
		int total = 0;

		for (int i = value.indexOf(needle); i >= 0; i = value.indexOf(needle, i + needle.length())) {
			total++;
		}

		return total;
	}
}