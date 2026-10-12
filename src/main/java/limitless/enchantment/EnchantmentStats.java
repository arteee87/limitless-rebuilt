package limitless.enchantment;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import limitless.config.Configuration;
import limitless.config.enchantment.entry.EnchantmentTooltipConfiguration;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelBasedValue;
import net.minecraft.enchantment.effect.EnchantmentEffectEntry;
import net.minecraft.enchantment.effect.EnchantmentValueEffect;
import net.minecraft.enchantment.effect.value.AddEnchantmentEffect;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;

/**
 * What the enchantments on a stack actually add, for display in the item tooltip.
 *
 * <p>Sharpness, knockback and the protection family sit on value-effect channels and are resolved
 * when the effect fires, so no attribute line ever mentions them. Reading those channels is the
 * only way to show the real numbers.
 *
 * <p>The channel does not determine the line, though. Sharpness, smite, bane of arthropods and
 * impaling all contribute to {@code minecraft:damage}, yet they answer to different targets, so the
 * identifier is mapped to a category by hand rather than derived from the data.
 */
public final class EnchantmentStats {
	/** Which configuration switch governs a line. */
	public enum Channel {
		DAMAGE, KNOCKBACK, PROTECTION
	}

	/** One tooltip line: a translation key and the number it takes. */
	public record Stat(Channel channel, String key, float value) {
	}

	/** What makes two lines the same line: everything but the number. */
	private record Line(Channel channel, String key) {
	}

	private record Category(Channel channel, String key) {
	}

	private static final String SHARPNESS = "minecraft:sharpness";

	/**
	 * Enchantment identifier to line. The four damage enchantments share a channel but answer to
	 * different targets, so they get their own rows; knockback and punch share one.
	 */
	private static final Map<String, Category> CATEGORIES = Map.ofEntries(
			Map.entry("minecraft:smite", new Category(Channel.DAMAGE, "tooltip.limitless.damage.undead")),
			Map.entry("minecraft:bane_of_arthropods", new Category(Channel.DAMAGE, "tooltip.limitless.damage.arthropods")),
			Map.entry("minecraft:impaling", new Category(Channel.DAMAGE, "tooltip.limitless.damage.aquatic")),
			Map.entry("minecraft:power", new Category(Channel.DAMAGE, "tooltip.limitless.damage.projectile")),
			Map.entry("minecraft:knockback", new Category(Channel.KNOCKBACK, "tooltip.limitless.knockback")),
			Map.entry("minecraft:punch", new Category(Channel.KNOCKBACK, "tooltip.limitless.knockback")),
			Map.entry("minecraft:protection", new Category(Channel.PROTECTION, "tooltip.limitless.protection")),
			Map.entry("minecraft:fire_protection", new Category(Channel.PROTECTION, "tooltip.limitless.protection.fire")),
			Map.entry("minecraft:blast_protection", new Category(Channel.PROTECTION, "tooltip.limitless.protection.blast")),
			Map.entry("minecraft:projectile_protection", new Category(Channel.PROTECTION, "tooltip.limitless.protection.projectile")),
			Map.entry("minecraft:feather_falling", new Category(Channel.PROTECTION, "tooltip.limitless.protection.fall")));

	private EnchantmentStats() {
	}

	/**
	 * Damage sharpness adds, to be folded into the attack damage line rather than shown on its own.
	 */
	public static double sharpnessBonus(ItemStack stack) {
		double total = 0.0;

		for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : enchantments(stack)) {
			if (identifier(entry.getKey()).equals(SHARPNESS)) {
				total += sum(entry.getKey().value(), EnchantmentEffectComponentTypes.DAMAGE, entry.getIntValue());
			}
		}

		return total;
	}

	/**
	 * Every line the tooltip should add below the attribute block, in a stable order.
	 *
	 * <p>Two enchantments can answer to the same line — punch and knockback are both
	 * {@code "+X Attack Knockback"} — so the numbers are accumulated per rendered line rather than per
	 * enchantment. Punch IX and Knockback X print as one {@code +19}, not as a {@code +9} above a
	 * {@code +10}.
	 *
	 * <p>Nothing is read off {@code attribute_effects}: vanilla already prints those in its own block,
	 * with the value and the unit, so a second copy here would only be a duplicate.
	 */
	public static List<Stat> collect(ItemStack stack) {
		EnchantmentTooltipConfiguration configuration = Configuration.instance.enchantment.tooltip;

		if (configuration == null) {
			return List.of();
		}

		Map<Line, Float> merged = new LinkedHashMap<>();

		for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : enchantments(stack)) {
			String id = identifier(entry.getKey());

			if (id.equals(SHARPNESS)) {
				continue; // folded into the attack damage line
			}

			Category category = CATEGORIES.get(id);

			if (category != null && isEnabled(configuration, category.channel())) {
				float value = sum(entry.getKey().value(), channel(category.channel()), entry.getIntValue());

				if (value != 0.0F) {
					merged.merge(new Line(category.channel(), category.key()), value, Float::sum);
				}
			}
		}

		List<Stat> stats = new ArrayList<>(merged.size());

		merged.forEach((line, value) -> {
			if (value != 0.0F) {
				stats.add(new Stat(line.channel(), line.key(), value));
			}
		});

		return stats;
	}

	private static float sum(Enchantment enchantment,
			net.minecraft.component.ComponentType<List<EnchantmentEffectEntry<EnchantmentValueEffect>>> channel,
			int level) {
		float total = 0.0F;

		for (EnchantmentEffectEntry<EnchantmentValueEffect> entry : enchantment.getEffect(channel)) {
			if (entry.effect() instanceof AddEnchantmentEffect add) {
				EnchantmentLevelBasedValue value = add.value();
				total += value.getValue(level);
			}
		}

		return total;
	}

	private static List<Object2IntMap.Entry<RegistryEntry<Enchantment>>> enchantments(ItemStack stack) {
		ItemEnchantmentsComponent component = stack.get(DataComponentTypes.ENCHANTMENTS);

		if (component == null || component.isEmpty()) {
			return List.of();
		}

		return List.copyOf(component.getEnchantmentEntries());
	}

	private static net.minecraft.component.ComponentType<List<EnchantmentEffectEntry<EnchantmentValueEffect>>> channel(Channel channel) {
		return switch (channel) {
			case DAMAGE -> EnchantmentEffectComponentTypes.DAMAGE;
			case KNOCKBACK -> EnchantmentEffectComponentTypes.KNOCKBACK;
			case PROTECTION -> EnchantmentEffectComponentTypes.DAMAGE_PROTECTION;
		};
	}

	private static boolean isEnabled(EnchantmentTooltipConfiguration configuration, Channel channel) {
		return switch (channel) {
			case DAMAGE -> configuration.showDamage;
			case KNOCKBACK -> configuration.showKnockback;
			case PROTECTION -> configuration.showProtection;
		};
	}

	private static String identifier(RegistryEntry<Enchantment> entry) {
		return entry.getKey()
				.map(key -> key.getValue().toString())
				.orElse("");
	}

	/**
	 * Plain decimal without an exponent: levels are bounded only by the configuration, so sharpness
	 * can reach a value that {@code Double#toString} would render as {@code 1.073741824E9}.
	 */
	public static String format(float value) {
		float magnitude = Math.abs(value);

		if (magnitude < 1.0E9F && value == Math.rint(value)) {
			return Long.toString((long) value);
		}

		return new java.text.DecimalFormat("#.##", java.text.DecimalFormatSymbols.getInstance(Locale.ROOT)).format(value);
	}
}