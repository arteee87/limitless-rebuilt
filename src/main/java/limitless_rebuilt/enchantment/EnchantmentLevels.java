package limitless_rebuilt.enchantment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import limitless_rebuilt.LimitlessLogger;
import limitless_rebuilt.config.Configuration;
import limitless_rebuilt.config.enchantment.EnchantmentConfiguration;
import limitless_rebuilt.config.enchantment.entry.EnchantmentEntry;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

/**
 * Pushes the configured level limits into {@link EnchantmentLimits}.
 *
 * <p>1.19.3 did this from a {@code SimpleRegistry#set} injection, which no longer exists after the
 * registry rework; here it happens when the server (and the client) start and on every reload.
 *
 * <p>An enchantment whose minimum power curve grows with its level gets no ceiling at all, one
 * whose power is flat keeps the vanilla maximum: that distinction is what {@code SimpleRegistryMixin}
 * used to make while registering.
 */
public final class EnchantmentLevels {
	private static final RegistryKey<Registry<Enchantment>> KEY = RegistryKeys.ENCHANTMENT;
	private static final int MAX_POWER_SCAN = 1000;

	/**
	 * Defaults that deliberately differ from the vanilla ceiling.
	 *
	 * <p>Vanilla caps wind burst at three because its knockback multiplier is a lookup table of
	 * exactly three values — but a linear formula sits behind that table and starts being read from
	 * level four on, so levels four and five are values the game can genuinely produce. The automatic
	 * scan below cannot see this: wind burst has no {@code primary_items} and no growing
	 * {@code minPower}, so nothing in its data ever trips.
	 */
	private static final Map<String, Integer> DEFAULT_MAX_LEVELS = Map.of("minecraft:wind_burst", 5);

	private static volatile Registry<Enchantment> registry;

	private EnchantmentLevels() {
	}

	/** Binds to a manager and applies the configuration; used by both the server and the client. */
	public static void reload(DynamicRegistryManager manager) {
		if (manager == null) {
			return;
		}

		registry = manager.getOrThrow(KEY);
		apply(registry);
	}

	/** Re-applies the configuration to the enchantments seen the last time the mod started. */
	public static void reload() {
		if (registry != null) {
			apply(registry);
		}
	}

	/** Re-applies the configuration to every enchantment of the given registry. */
	public static void apply(Registry<Enchantment> registry) {
		EnchantmentConfiguration configuration = Configuration.instance.enchantment;
		List<EnchantmentEntry> added = new ArrayList<>();

		for (Map.Entry<RegistryKey<Enchantment>, Enchantment> entry : registry.getEntrySet()) {
			String identifier = entry.getKey().getValue().toString();
			Enchantment enchantment = entry.getValue();
			EnchantmentEntry configured = configuration.entry(identifier);

			if (configured == null) {
				configured = new EnchantmentEntry(identifier, defaultMaxLevel(identifier, enchantment));
				added.add(configured);
			}

			EnchantmentLimits.set(enchantment, configured.maxLevel, configured.useGlobalMaxLevel);
		}

		if (!added.isEmpty()) {
			configuration.maxLevels.addAll(added);
			configuration.maxLevels.sort(EnchantmentEntry::compareTo);
			Configuration.save();
			LimitlessLogger.info("Added %d enchantment entries to the configuration.".formatted(added.size()));
		}
	}

	/**
	 * The default offered in the per-enchantment configuration for an enchantment the file has not
	 * been told about yet.
	 *
	 * <p>{@link #DEFAULT_MAX_LEVELS} wins where it applies. Otherwise: {@link Integer#MAX_VALUE} when
	 * the enchantment can be levelled further because its power requirement keeps growing, the
	 * vanilla maximum when it is a single level enchantment, and the vanilla maximum otherwise.
	 */
	private static int defaultMaxLevel(String identifier, Enchantment enchantment) {
		Integer override = DEFAULT_MAX_LEVELS.get(identifier);

		if (override != null) {
			return override;
		}

		int original = EnchantmentLimits.originalMaxLevel(enchantment);

		if (original <= 1) {
			return Math.max(original, 1);
		}

		int minLevel = enchantment.getMinLevel();
		int iterations = Math.min(MAX_POWER_SCAN, original - minLevel);
		int previousPower = enchantment.getMinPower(minLevel);

		for (int level = 1; level <= iterations; level++) {
			int power = enchantment.getMinPower(minLevel + level);

			if (previousPower < power) {
				return Integer.MAX_VALUE;
			}

			previousPower = power;
		}

		return original;
	}
}