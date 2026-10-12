package limitless.enchantment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The vanilla step cost is {@code 2n + 7} below level 15, {@code 5n - 38} up to level 30 and
 * {@code 9n - 158} above it; {@code ExperienceUtil} has to reproduce those sums exactly, because
 * the anvil cost is derived from them.
 */
class ExperienceUtilTest {
	@ParameterizedTest(name = "reaching level {0} costs {1} experience")
	@CsvSource({
			"1, 7",
			"2, 16",
			"3, 27",
			"15, 315",
			"16, 352",
			"17, 394",
			"30, 1395",
			"31, 1507",
			"32, 1628",
	})
	@DisplayName("normalizedExperience matches the vanilla progression")
	void normalizedExperienceMatchesVanilla(int level, long expected) {
		assertEquals(expected, ExperienceUtil.normalizedExperience(level));
	}

	@ParameterizedTest(name = "the step from level {0} costs {1}")
	@CsvSource({
			"0, 7",
			"1, 9",
			"14, 35",
			"15, 37",
			"16, 42",
			"29, 107",
			"30, 112",
			"31, 121",
			"100, 742",
	})
	@DisplayName("every step equals the vanilla 2n+7 / 5n-38 / 9n-158 curves")
	void stepsMatchVanilla(int level, int expected) {
		assertEquals(expected, ExperienceUtil.normalizedExperience(level + 1) - ExperienceUtil.normalizedExperience(level));
	}

	@Test
	@DisplayName("the step above level 30 grows by 9 per level")
	void aboveThirty() {
		int first = (int) (ExperienceUtil.normalizedExperience(31) - ExperienceUtil.normalizedExperience(30));
		int tenth = (int) (ExperienceUtil.normalizedExperience(40) - ExperienceUtil.normalizedExperience(39));

		assertEquals(112, first);
		assertEquals(first + 9 * 9, tenth);
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 5, 15, 29, 30, 31, 100, -7})
	@DisplayName("normalizedDifference is symmetric and drops the sign")
	void normalizedDifferenceIsSymmetric(int level) {
		assertEquals(
				ExperienceUtil.normalizedDifference(level, 40),
				ExperienceUtil.normalizedDifference(40, level));
		assertTrue(ExperienceUtil.normalizedDifference(level, 40) >= 0L);
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 15, 30, 31, 500})
	@DisplayName("the difference of a level with itself is zero")
	void differenceWithItselfIsZero(int level) {
		assertEquals(0L, ExperienceUtil.normalizedDifference(level, level));
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 15, 30, 31, 500})
	@DisplayName("negative levels are treated by their absolute value")
	void negativeLevels(int level) {
		assertEquals(
				ExperienceUtil.normalizedDifference(level, 30),
				ExperienceUtil.normalizedDifference(-level, -30));
	}

	@Test
	@DisplayName("normalizedExperience grows monotonically")
	void monotonic() {
		long previous = -1L;

		for (int level = 0; level < 200; level++) {
			long value = ExperienceUtil.normalizedExperience(level);
			assertTrue(value > previous, "level " + level + " went backwards");
			previous = value;
		}
	}

	@Test
	@DisplayName("partial progress scales the total")
	void partialProgress() {
		assertEquals(
				ExperienceUtil.normalizedExperience(16)
						+ (long) (0.5D * ExperienceUtil.normalizedExperience(17)),
				ExperienceUtil.normalizedExperience(16, 0.5D));
		assertEquals(ExperienceUtil.normalizedExperience(16), ExperienceUtil.normalizedExperience(16, 0.0D));
	}

	@Test
	@DisplayName("very high levels still resolve without overflowing")
	void hugeLevel() {
		assertTrue(ExperienceUtil.normalizedExperience(100000) > 0L);
		assertTrue(ExperienceUtil.normalizedExperience(1000000) > ExperienceUtil.normalizedExperience(999999));
	}

	/**
	 * Paying {@code count} levels above the offset has to remove exactly the experience that
	 * separates {@code offset - count} from {@code offset}.
	 *
	 * <p>{@code addExperienceLevelsRelatively} does that by feeding one segment at a time to
	 * {@code PlayerEntity#addExperience(int)}; a port that replaced that sink with a direct field
	 * write kept the segments correct but dropped the level, so this is the invariant that broke
	 * silently. It only needs the arithmetic, not a player.
	 */
	@ParameterizedTest
	@ValueSource(ints = {1, 2, 3})
	@DisplayName("paying above the offset removes exactly one level's worth of experience")
	void payingRemovesTheRightAmountOfExperience(int count) {
		int offset = 30;
		long removed = 0L;

		for (int level = offset - count; level != offset; level++) {
			removed += ExperienceUtil.normalizedExperience(level + 1)
					- ExperienceUtil.normalizedExperience(level);
		}

		assertEquals(
				ExperienceUtil.normalizedExperience(offset)
						- ExperienceUtil.normalizedExperience(offset - count),
				removed);
	}
}