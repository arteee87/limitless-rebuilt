package limitless.enchantment;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.enchantment.Enchantment;

/**
 * Per enchantment level limits.
 *
 * <p>1.19.3 stored two extra fields on every {@code Enchantment} instance, which needed bytecode
 * rewriting because the class could not be mixed into directly. In 1.21.4 {@code Enchantment} is a
 * {@code final record}, so the state lives here instead and {@code EnchantmentMixin} only reads it.
 *
 * <p>Keys are compared by identity: {@code Enchantment} is a record, so its generated
 * {@code equals} walks the description, the definition and the effects of both operands.
 */
public final class EnchantmentLimits {
	public record Limit(int maxLevel, boolean useGlobalMaxLevel, int originalMaxLevel) {
	}

	private static final Map<Enchantment, Limit> LIMITS = new IdentityHashMap<>();

	private EnchantmentLimits() {
	}

	public static Limit get(Enchantment enchantment) {
		synchronized (LIMITS) {
			return LIMITS.get(enchantment);
		}
	}

	public static void set(Enchantment enchantment, int maxLevel, boolean useGlobalMaxLevel) {
		int original = originalMaxLevel(enchantment);

		synchronized (LIMITS) {
			LIMITS.put(enchantment, new Limit(maxLevel, useGlobalMaxLevel, original));
		}
	}

	/** Number of enchantments that currently carry a limit; the debug log compares it per slot. */
	public static int size() {
		synchronized (LIMITS) {
			return LIMITS.size();
		}
	}

	public static void clear() {
		synchronized (LIMITS) {
			LIMITS.clear();
		}
	}

	/**
	 * Vanilla maximum of an enchantment.
	 *
	 * <p>Read before any limit is applied, which is why it is cached separately: once
	 * {@code EnchantmentMixin} starts answering with the configured value, calling
	 * {@code getMaxLevel()} again would only ever return the modified number.
	 */
	public static int originalMaxLevel(Enchantment enchantment) {
		synchronized (LIMITS) {
			Limit limit = LIMITS.get(enchantment);

			if (limit != null) {
				return limit.originalMaxLevel();
			}
		}

		return enchantment.getMaxLevel();
	}
}