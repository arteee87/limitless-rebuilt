package limitless_rebuilt.enchantment;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ExperienceUtilCapTest {
	/** What vanilla can ask for: {@code max(level, bookshelfCount * 2)} with at most fifteen bookcases. */
	@Test
	@DisplayName("the cap is the vanilla ceiling")
	void capIsVanilla() {
		assertEquals(30, ExperienceUtil.VANILLA_MAX_REQUIREMENT);
	}

	/**
	 * Below the ceiling a fully powered table has to behave exactly like vanilla, which is what makes
	 * the default of thirty a no-op for ordinary builds.
	 */
	@ParameterizedTest
	@ValueSource(ints = {0, 1, 2, 5, 14, 29, 30})
	@DisplayName("values up to the cap pass through untouched")
	void belowCap(int power) {
		assertEquals(power, ExperienceUtil.capRequiredLevel(power, 30));
	}

	@ParameterizedTest
	@ValueSource(ints = {31, 40, 1024, Integer.MAX_VALUE})
	@DisplayName("a stronger table is held at the cap")
	void aboveCap(int power) {
		assertEquals(30, ExperienceUtil.capRequiredLevel(power, 30));
	}

	/** A hand-edited config must not be able to raise the requirement past vanilla either. */
	@ParameterizedTest
	@ValueSource(ints = {31, 100, 9999, Integer.MAX_VALUE})
	@DisplayName("a limit above the cap cannot exceed the cap")
	void limitAboveCap(int limit) {
		assertEquals(ExperienceUtil.VANILLA_MAX_REQUIREMENT,
				ExperienceUtil.capRequiredLevel(1000, limit));
	}

	@ParameterizedTest
	@CsvSource({"14, 5, 5", "14, 30, 14", "1024, 30, 30", "1024, 1, 1"})
	@DisplayName("the lower of the two wins")
	void lowerOfPowerAndLimit(int power, int limit, int expected) {
		assertEquals(expected, ExperienceUtil.capRequiredLevel(power, limit));
	}

	/**
	 * Zero and negative collapse the enchantment level part of the requirement; vanilla's separate
	 * {@code experienceLevel < id + 1} check is not touched, so the table still costs 1, 2 or 3.
	 */
	@ParameterizedTest
	@ValueSource(ints = {0, -1, -30})
	@DisplayName("zero and negative limits demand nothing on their own")
	void zeroOrNegative(int limit) {
		assertEquals(0, ExperienceUtil.capRequiredLevel(1024, limit));
	}

	/**
	 * The client screen and the server both read this array, so both have to be handed the same
	 * numbers. The original array must come back untouched: it is also the enchantment level the
	 * generator uses.
	 */
	@Test
	@DisplayName("the array variant caps every slot and leaves the original alone")
	void arrayVariant() {
		int[] original = {39, 83, 164};
		int[] capped = ExperienceUtil.capRequiredLevels(original, 30);

		assertArrayEquals(new int[]{30, 30, 30}, capped);
		assertArrayEquals(new int[]{39, 83, 164}, original);
	}

	@Test
	@DisplayName("the array variant keeps slots that are already low")
	void arrayVariantKeepsLowSlots() {
		assertArrayEquals(new int[]{0, 5, 29, 30},
				ExperienceUtil.capRequiredLevels(new int[]{0, 5, 29, 90}, 30));
	}
}