package limitless.enchantment;

/**
 * Roman numerals for enchantment levels.
 *
 * <p>Vanilla renders a level through {@code enchantment.level.<n>}, and {@code en_us.json} defines
 * only {@code 1} to {@code 10}: anything above that is displayed as the raw key, which is what a
 * level 14 enchantment looks like out of the box. Since the mod has no upper bound on a level, the
 * numeral is computed instead of looked up.
 *
 * <p>Roman notation has no symbol above a thousand, so the classic maximum is {@value #MAX}. Levels
 * above it are returned as digits: the configured maximum is {@link Integer#MAX_VALUE}, whose
 * proper Roman form would be over two million characters long, while {@code Sharpness 5000} is both
 * shorter and easier to read than any overline notation the vanilla font cannot render anyway.
 */
public final class RomanNumerals {
	/** Last level written in Roman numerals; the classic maximum of the notation. */
	public static final int MAX = 3999;

	private static final int[] VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
	private static final String[] SYMBOLS = {
			"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"
	};

	private RomanNumerals() {
	}

	/** Level 1 renders as "I", the way vanilla does; anything outside the range falls back to digits. */
	public static String of(int level) {
		if (level <= 0 || level > MAX) {
			return Integer.toString(level);
		}

		StringBuilder builder = new StringBuilder();

		for (int i = 0; level > 0; i++) {
			while (level >= VALUES[i]) {
				builder.append(SYMBOLS[i]);
				level -= VALUES[i];
			}
		}

		return builder.toString();
	}
}