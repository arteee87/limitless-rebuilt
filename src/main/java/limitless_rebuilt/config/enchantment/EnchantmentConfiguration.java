package limitless_rebuilt.config.enchantment;

import java.util.ArrayList;
import java.util.List;
import limitless_rebuilt.config.enchantment.entry.EnchantingConflicts;
import limitless_rebuilt.enchantment.ExperienceUtil;
import limitless_rebuilt.config.enchantment.entry.EnchantmentEntry;
import limitless_rebuilt.config.enchantment.entry.EnchantmentParticleConfiguration;
import limitless_rebuilt.config.enchantment.entry.EnchantmentTooltipConfiguration;
import limitless_rebuilt.config.enchantment.entry.ReenchantingConfiguration;
import limitless_rebuilt.config.enchantment.entry.normalization.EnchantmentNormalizationEntry;
import limitless_rebuilt.config.enchantment.entry.radius.HorizontalRadius;
import limitless_rebuilt.config.enchantment.entry.radius.Radius;
import limitless_rebuilt.config.enchantment.entry.radius.VerticalRadius;

/**
 * Enchanting behaviour.
 *
 * <p>This class only holds data: resolving enchantment identifiers against the registry and
 * pushing the resulting limits into {@code Enchantment} instances is done by
 * {@code limitless.enchantment.EnchantmentLevels}, so that the configuration stays testable
 * without a bootstrapped game.
 */
public final class EnchantmentConfiguration {
	public int globalMaxLevel = Integer.MAX_VALUE;

	/**
	 * Highest number of experience levels the enchanting table may demand.
	 *
	 * <p>Vanilla asks for {@code max(level, bookshelfCount * 2)}, and fifteen bookcases is the most it
	 * can count, so its own ceiling is {@value ExperienceUtil#VANILLA_MAX_REQUIREMENT}. The mod removes
	 * that ceiling, which would otherwise let a heavily powered table demand a thousand levels for the
	 * price of three; this setting puts a bound back and is itself capped at the vanilla one.
	 */
	public int maxLevelRequirement = ExperienceUtil.VANILLA_MAX_REQUIREMENT;

	public boolean allowTreasure = true;
	public boolean revealEnchantments;
	public EnchantingConflicts conflicts = new EnchantingConflicts();
	public EnchantmentNormalizationEntry normalization = new EnchantmentNormalizationEntry();
	public ReenchantingConfiguration reenchanting = new ReenchantingConfiguration();
	public EnchantingBlockConfiguration enchantingBlocks = new EnchantingBlockConfiguration();
	public EnchantmentParticleConfiguration particles = new EnchantmentParticleConfiguration();
	public EnchantmentTooltipConfiguration tooltip = new EnchantmentTooltipConfiguration();
	public List<EnchantmentEntry> maxLevels = new ArrayList<>();

	/** Per enchantment overrides shipped with the mod; re-applied on every load. */
	public static List<EnchantmentEntry> defaultEntries() {
		List<EnchantmentEntry> defaults = new ArrayList<>();
		defaults.add(new EnchantmentEntry("minecraft:luck_of_the_sea", 84));
		defaults.add(new EnchantmentEntry("minecraft:lure", 3));
		defaults.add(new EnchantmentEntry("minecraft:quick_charge", 5));
		return defaults;
	}

	/**
	 * Restores the defaults 2.0.0 shipped with, drops unparsable identifiers and makes sure every
	 * enchantment known to the registry ends up with an entry.
	 */
	public void postLoad() {
		if (this.conflicts == null) {
			this.conflicts = new EnchantingConflicts();
		}

		if (this.normalization == null) {
			this.normalization = new EnchantmentNormalizationEntry();
		}

		if (this.reenchanting == null) {
			this.reenchanting = new ReenchantingConfiguration();
		}

		if (this.enchantingBlocks == null) {
			this.enchantingBlocks = new EnchantingBlockConfiguration();
		}

		if (this.particles == null) {
			this.particles = new EnchantmentParticleConfiguration();
		}

		applyDefaults(this.particles.radius);
		applyDefaults(this.enchantingBlocks.radius);

		if (this.maxLevels == null) {
			this.maxLevels = new ArrayList<>();
		}

		this.maxLevels.removeIf(entry -> entry == null || entry.identifier == null);

		for (EnchantmentEntry defaultEntry : defaultEntries()) {
			if (entry(defaultEntry.identifier) == null) {
				this.maxLevels.add(defaultEntry);
			}
		}

		this.maxLevels.sort(EnchantmentEntry::compareTo);
		this.enchantingBlocks.postLoad();
	}

	private static void applyDefaults(Radius radius) {
		if (radius == null) {
			return;
		}

		if (radius.xz == null) {
			radius.xz = new HorizontalRadius();
		}

		if (radius.y == null) {
			radius.y = new VerticalRadius();
		}
	}

	public EnchantmentEntry entry(String identifier) {
		for (EnchantmentEntry entry : this.maxLevels) {
			if (entry.identifier != null && entry.identifier.equals(identifier)) {
				return entry;
			}
		}

		return null;
	}
}