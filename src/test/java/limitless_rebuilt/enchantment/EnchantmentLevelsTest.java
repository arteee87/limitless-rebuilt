package limitless_rebuilt.enchantment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The level a freshly created entry is offered.
 *
 * <p>Kept separate from the rest of the suite because {@code EnchantmentLevels} reaches the enchantment
 * registry, which cannot boot in a unit test. The ceiling itself is pure arithmetic, and it is worth
 * guarding on its own: an unguarded default handed the enchanting table {@link Integer#MAX_VALUE},
 * which it then offered literally — a Wind Burst 2147483647 reports a knockback multiplier of seven
 * hundred million and the hit throws the target out of the world instead of damaging it.
 */
class EnchantmentLevelsTest {
	private static int clamp(int level) throws Exception {
		Method method = EnchantmentLevels.class.getDeclaredMethod("clamp", int.class);
		method.setAccessible(true);
		return (int) method.invoke(null, level);
	}

	@ParameterizedTest
	@ValueSource(ints = {Integer.MIN_VALUE, -100, 0, 1})
	@DisplayName("a level below one is raised to one")
	void floorIsOne(int level) throws Exception {
		assertEquals(1, clamp(level));
	}

	/**
	 * The reported fault: the power scan answered {@link Integer#MAX_VALUE} for every enchantment whose
	 * minimum power keeps growing, and nothing brought it back down.
	 */
	@Test
	@DisplayName("the scan's answer is pulled down to the ceiling")
	void integerMaxValueIsClamped() throws Exception {
		assertEquals(100, clamp(Integer.MAX_VALUE));
	}

	@ParameterizedTest
	@CsvSource({"2, 2", "5, 5", "19, 19", "100, 100"})
	@DisplayName("a level already within the ceiling is untouched")
	void insideTheCeiling(int level, int expected) throws Exception {
		assertEquals(expected, clamp(level));
	}

	@Test
	@DisplayName("the ceiling is a hundred")
	void ceilingValue() throws Exception {
		Field ceiling = EnchantmentLevels.class.getDeclaredField("DEFAULT_CEILING");
		ceiling.setAccessible(true);

		assertEquals(100, ceiling.getInt(null));
	}

	/**
	 * A knockback strength is a velocity, not a damage number: {@code takeKnockback} sets
	 * {@code normalise(direction) * strength}, so a hundred throws the target out of the world. Wind
	 * burst goes the other way, up to five, because its lookup table only has three entries.
	 */
	@Test
	@DisplayName("the enchantments that break the game on their own are overridden")
	void overrides() throws Exception {
		Field table = EnchantmentLevels.class.getDeclaredField("DEFAULT_MAX_LEVELS");
		table.setAccessible(true);

		@SuppressWarnings("unchecked")
		Map<String, Integer> overrides = (Map<String, Integer>) table.get(null);

		assertEquals(10, overrides.get("minecraft:knockback"));
		assertEquals(10, overrides.get("minecraft:punch"));
		assertEquals(5, overrides.get("minecraft:wind_burst"));
	}
}