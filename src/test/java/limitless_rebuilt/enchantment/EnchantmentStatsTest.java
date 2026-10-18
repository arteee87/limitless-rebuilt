package limitless_rebuilt.enchantment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import limitless_rebuilt.ModResources;
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

	@SuppressWarnings("unchecked")
	private static List<Object> lines(String id) throws Exception {
		Object entry = categories().get(id);
		return entry == null ? List.of() : (List<Object>) entry;
	}

	private static Object only(String id) throws Exception {
		List<Object> lines = lines(id);

		assertEquals(1, lines.size(), id + " should earn exactly one line");

		return lines.getFirst();
	}

	private static Object field(Object target, String name) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	private static String key(Object category) throws Exception {
		return (String) field(category, "key");
	}

	private static int orderOf(String id) throws Exception {
		Method method = EnchantmentStats.class.getDeclaredMethod("orderOf", String.class);
		method.setAccessible(true);
		return (int) method.invoke(null, id);
	}

	/**
	 * The order of the lines has to be a property of the enchantment, not of the hash map the
	 * enchantments happen to come back in. Two items differing only in a level used to print the same
	 * lines in two different orders, and a screenshot was the only thing that caught it.
	 *
	 * <p>What this guards is that the order is total: every mapped enchantment gets its own position,
	 * and those positions run in the order this class declares its rows. That declaration is also the
	 * order {@code orderOf} falls back to before {@code #minecraft:tooltip_order} has been read, which
	 * is the state this runs in — nothing here calls {@code reload}.
	 */
	@Test
	@DisplayName("every mapped enchantment has its own place in the line order")
	void lineOrderIsTotal() throws Exception {
		List<String> declared = List.copyOf(categories().keySet());

		assertEquals(13, declared.size(), "a row was added or dropped without the test following");

		int previous = -1;

		for (String id : declared) {
			int position = orderOf(id);

			assertTrue(position > previous, id + " does not sort after the row declared before it");
			previous = position;
		}
	}

	private static EnchantmentStats.Channel channelOf(Object category) throws Exception {
		Object value = field(category, "value");
		return EnchantmentStats.channelOf((EnchantmentStats.Value) value);
	}

	private static EnchantmentStats.Style styleOf(Object category) throws Exception {
		return (EnchantmentStats.Style) field(category, "style");
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
			"minecraft:density, DAMAGE",
	})
	@DisplayName("every enchantment that deserves one line has a category")
	void mappedEnchantments(String id, EnchantmentStats.Channel expected) throws Exception {
		assertEquals(expected, channelOf(only(id)));
	}

	@Test
	@DisplayName("sharpness has no line of its own")
	void sharpnessIsFoldedIntoAttackDamage() throws Exception {
		assertEquals(null, categories().get("minecraft:sharpness"));
	}

	/**
	 * Wind burst is the only enchantment earning two. Both answers come out of the same explosion
	 * effect, but they are not quantities of the same thing: one scales the knockback dealt, the other
	 * is the radius of the blast. Collapsing them onto a single row would mean picking one to hide.
	 */
	@Test
	@DisplayName("wind burst earns two lines, from different channels")
	void windBurstEarnsTwoLines() throws Exception {
		List<Object> lines = lines("minecraft:wind_burst");

		assertEquals(2, lines.size(), "wind burst should earn exactly two lines");
		assertEquals(Set.of(EnchantmentStats.Channel.KNOCKBACK, EnchantmentStats.Channel.DAMAGE),
				Set.of(channelOf(lines.get(0)), channelOf(lines.get(1))));
		assertNotEquals(key(lines.get(0)), key(lines.get(1)));
	}

	/**
	 * The mace lines answer a rate and a coefficient. A bare {@code +3} beside {@code +4.50 Damage to
	 * Undead} reads as a different quantity, so these three — and only these three — carry two
	 * decimals. Widening {@code format} itself would change every line already in front of players.
	 */
	@Test
	@DisplayName("every mace line is fixed, and no other line is")
	void onlyMaceLinesAreFixed() throws Exception {
		Set<String> maceKeys = Set.of("tooltip.limitless.smash_damage",
				"tooltip.limitless.self_knockback", "tooltip.limitless.impact_radius");

		for (Object entry : categories().values()) {
			for (Object category : (List<?>) entry) {
				boolean mace = maceKeys.contains(key(category));
				EnchantmentStats.Style expected = mace ? EnchantmentStats.Style.FIXED : EnchantmentStats.Style.PLAIN;

				assertEquals(expected, styleOf(category), key(category));
			}
		}
	}

	/** The 29 enchantments deliberately left out, checked so none creeps back by accident. */
	@ParameterizedTest
	@ValueSource(strings = {
			"minecraft:binding_curse", "minecraft:vanishing_curse", "minecraft:looting",
			"minecraft:luck_of_the_sea", "minecraft:lure", "minecraft:loyalty", "minecraft:riptide",
			"minecraft:quick_charge", "minecraft:silk_touch", "minecraft:fortune",
			"minecraft:infinity", "minecraft:unbreaking", "minecraft:mending", "minecraft:channeling",
			"minecraft:flame", "minecraft:fire_aspect", "minecraft:frost_walker",
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

	@ParameterizedTest
	@CsvSource({"0, 0.00", "3, 3.00", "3.5, 3.50", "-2.5, -2.50", "2.9, 2.90", "0.5, 0.50"})
	@DisplayName("the fixed style keeps two decimals where the plain style drops them")
	void fixedFormatting(double value, String expected) {
		assertEquals(expected, EnchantmentStats.formatFixed((float) value));
	}

	/** The reason there are two formatters: the old text must not move. */
	@ParameterizedTest
	@CsvSource({"3, PLAIN, 3", "3, FIXED, 3.00", "3.5, PLAIN, 3.5", "3.5, FIXED, 3.50"})
	@DisplayName("render picks the formatter the line asked for")
	void rendering(float value, EnchantmentStats.Style style, String expected) {
		EnchantmentStats.Stat stat = new EnchantmentStats.Stat(
				EnchantmentStats.Channel.DAMAGE, "tooltip.limitless.knockback", value, style);

		assertEquals(expected, EnchantmentStats.render(stat));
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
		assertEquals(key(only("minecraft:knockback")), key(only("minecraft:punch")));
	}

	/** Protection and feather falling answer to different rows, so they must not be folded together. */
	@Test
	@DisplayName("protection and feather falling stay on separate lines")
	void protectionLinesDiffer() throws Exception {
		assertNotEquals(key(only("minecraft:protection")), key(only("minecraft:feather_falling")));
	}

	/** Every key {@code collect} can produce: one per category, however many an enchantment earns. */
	private static Set<String> lineKeys() throws Exception {
		Set<String> keys = new LinkedHashSet<>();

		for (Object entry : categories().values()) {
			for (Object category : (List<?>) entry) {
				keys.add(key(category));
			}
		}

		return keys;
	}

	private static Map<String, String> lang() {
		return ModResources.lang();
	}

	private static int count(String value, String needle) {
		int total = 0;

		for (int i = value.indexOf(needle); i >= 0; i = value.indexOf(needle, i + needle.length())) {
			total++;
		}

		return total;
	}
}