package limitless_rebuilt.enchantment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RomanNumeralsTest {
	@ParameterizedTest
	@CsvSource({
			"1, I",
			"2, II",
			"3, III",
			"4, IV",
			"5, V",
			"6, VI",
			"7, VII",
			"8, VIII",
			"9, IX",
			"10, X",
	})
	void singleDigits(int level, String expected) {
		assertEquals(expected, RomanNumerals.of(level));
	}

	/**
	 * The last key vanilla defines is {@code enchantment.level.10}; anything above it used to be
	 * rendered as the raw key, which is the bug this replaces.
	 */
	@Test
	void aboveVanillaRange() {
		assertEquals("XI", RomanNumerals.of(11));
		assertEquals("XIV", RomanNumerals.of(14));
		assertEquals("XL", RomanNumerals.of(40));
		assertEquals("XC", RomanNumerals.of(90));
		assertEquals("CD", RomanNumerals.of(400));
		assertEquals("CM", RomanNumerals.of(900));
		assertEquals("MMMCMXCIX", RomanNumerals.of(3999));
	}

	/** Subtractive notation must not degrade into repeated symbols. */
	@Test
	void subtractivePairs() {
		assertEquals("IV", RomanNumerals.of(4));
		assertEquals("IX", RomanNumerals.of(9));
		assertEquals("XL", RomanNumerals.of(40));
		assertEquals("XC", RomanNumerals.of(90));
		assertEquals("CD", RomanNumerals.of(400));
		assertEquals("CM", RomanNumerals.of(900));
	}

	@ParameterizedTest
	@ValueSource(ints = {1001, 1987, 2026, 3999})
	void roundTripsWithinTheRange(int level) {
		assertEquals(level, fromRoman(RomanNumerals.of(level)));
	}

	/**
	 * Beyond the classic maximum of the notation the numeral falls back to digits: the configured
	 * maximum level is {@link Integer#MAX_VALUE}, and its Roman form would be over two million
	 * characters.
	 */
	@Test
	void aboveTheRomanRangeFallsBackToDigits() {
		assertEquals("MMMCMXCIX", RomanNumerals.of(3999));
		assertEquals("4000", RomanNumerals.of(4000));
		assertEquals("123456", RomanNumerals.of(123456));
		assertEquals(Integer.toString(Integer.MAX_VALUE), RomanNumerals.of(Integer.MAX_VALUE));
	}

	@Test
	void nonPositiveFallsBackToDigits() {
		assertEquals("0", RomanNumerals.of(0));
		assertEquals("-5", RomanNumerals.of(-5));
	}

	private static int fromRoman(String numeral) {
		int total = 0;

		for (int i = 0; i < numeral.length(); i++) {
			int value = valueOf(numeral.charAt(i));

			if (i + 1 < numeral.length() && valueOf(numeral.charAt(i + 1)) > value) {
				total -= value;
			} else {
				total += value;
			}
		}

		return total;
	}

	private static int valueOf(char symbol) {
		return switch (symbol) {
			case 'I' -> 1;
			case 'V' -> 5;
			case 'X' -> 10;
			case 'L' -> 50;
			case 'C' -> 100;
			case 'D' -> 500;
			case 'M' -> 1000;
			default -> 0;
		};
	}
}