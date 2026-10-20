package limitless_rebuilt.enchantment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import limitless_rebuilt.LimitlessLogger;
import limitless_rebuilt.config.Configuration;
import limitless_rebuilt.config.enchantment.EnchantmentConfiguration;
import limitless_rebuilt.config.enchantment.entry.EnchantmentEntry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

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
	private static final ResourceKey<Registry<Enchantment>> KEY = Registries.ENCHANTMENT;
	private static final int MAX_POWER_SCAN = 1000;

	/**
	 * Ceiling offered to a freshly created per-enchantment entry.
	 *
	 * <p>An enchantment whose power curve keeps growing used to land on {@link Integer#MAX_VALUE},
	 * which the enchanting table then offered literally: a Wind Burst 2147483647 reports a knockback
	 * multiplier of seven hundred million, and the hit that follows throws the target out of the world
	 * rather than damaging it. A hundred keeps the mechanic — the point of the mod — while leaving
	 * every number on a scale the game can survive.
	 *
	 * <p>This is the <em>default</em> only. The configuration field still accepts up to
	 * {@link Integer#MAX_VALUE}, so anybody who wants the old ceiling can type it in, and entries
	 * already in a file are never rewritten.
	 */
	private static final int DEFAULT_CEILING = 100;

	/**
	 * Defaults that deliberately differ from {@link #DEFAULT_CEILING}.
	 *
	 * <p>{@code wind_burst} goes up because vanilla caps it at three only because its knockback
	 * multiplier is a lookup table of exactly three values — a linear formula sits behind that table
	 * and starts being read from level four on, so four and five are values the game can genuinely
	 * produce. The automatic scan cannot see this: wind burst has no {@code primary_items} and no
	 * growing {@code minPower}, so nothing in its data ever trips.
	 *
	 * <p>{@code knockback} and {@code punch} go down because their value is a knockback strength, not
	 * a damage number: {@code LivingEntity#takeKnockback} sets a velocity of
	 * {@code normalise(direction) * strength}, so a level of a hundred is fifty times vanilla Knockback
	 * II and launches the target across the world. Ten is still loud, and stops short of ejecting
	 * anyone. Protection needs no entry of its own — {@code DamageUtil#getInflictedDamage} clamps it
	 * to 20 of 25, which level nineteen already reaches.
	 */
	private static final Map<String, Integer> DEFAULT_MAX_LEVELS = Map.of(
			"minecraft:knockback", 10,
			"minecraft:punch", 10,
			"minecraft:wind_burst", 5);

	private static volatile Registry<Enchantment> registry;

	private EnchantmentLevels() {
	}

	/** Binds to a manager and applies the configuration; used by both the server and the client. */
	public static void reload(RegistryAccess manager) {
		if (manager == null) {
			return;
		}

		registry = manager.lookupOrThrow(KEY);
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

		for (Map.Entry<ResourceKey<Enchantment>, Enchantment> entry : registry.entrySet()) {
			String identifier = entry.getKey().identifier().toString();
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
	 * <p>{@link #DEFAULT_MAX_LEVELS} wins where it applies. Otherwise the answer is
	 * {@link Integer#MAX_VALUE} when the enchantment can be levelled further because its power
	 * requirement keeps growing, or the vanilla maximum when it does not — and either way it comes
	 * back through {@link #clamp}, because a ceiling nobody asked for is still the right default.
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
		int previousPower = enchantment.getMinCost(minLevel);

		for (int level = 1; level <= iterations; level++) {
			int power = enchantment.getMinCost(minLevel + level);

			if (previousPower < power) {
				return clamp(Integer.MAX_VALUE);
			}

			previousPower = power;
		}

		return clamp(original);
	}

	/**
	 * Pulls a default down to {@link #DEFAULT_CEILING}, never below one.
	 *
	 * <p>Only defaults pass through here. A number somebody typed into the configuration is left
	 * exactly as it is, which is what keeps the ceiling of {@link Integer#MAX_VALUE} reachable on
	 * purpose.
	 */
	private static int clamp(int level) {
		return Math.min(Math.max(level, 1), DEFAULT_CEILING);
	}
}