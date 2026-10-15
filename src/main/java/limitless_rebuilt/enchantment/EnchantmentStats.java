package limitless_rebuilt.enchantment;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import limitless_rebuilt.config.Configuration;
import limitless_rebuilt.config.enchantment.entry.EnchantmentTooltipConfiguration;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelBasedValue;
import net.minecraft.enchantment.effect.EnchantmentEffectEntry;
import net.minecraft.enchantment.effect.EnchantmentEntityEffect;
import net.minecraft.enchantment.effect.EnchantmentValueEffect;
import net.minecraft.enchantment.effect.TargetedEnchantmentEffect;
import net.minecraft.enchantment.effect.entity.ExplodeEnchantmentEffect;
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

	/**
	 * How a number is rendered.
	 *
	 * <p>Lines answering a whole number print it bare, which is how they have always printed. The
	 * mace lines answer a rate or a coefficient, and a bare {@code +3} beside them would read as a
	 * different quantity, so those always carry two decimals.
	 */
	public enum Style {
		PLAIN, FIXED
	}

	/** One tooltip line: a translation key and the number it takes. */
	public record Stat(Channel channel, String key, float value, Style style) {
	}

	/** What makes two lines the same line: everything but the number. */
	private record Line(Channel channel, String key, Style style) {
	}

	private record Category(Value value, String key, Style style) {
	}

	/**
	 * Where a line reads its number from.
	 *
	 * <p>The first four are value-effect channels: a list of linear values the enchantment adds,
	 * summed at the level the stack carries. The last two are the exception. Wind burst nests both
	 * of its numbers inside the explosion it causes — one level deeper than any other enchantment
	 * goes — so neither has a component type of its own.
	 *
	 * <p>Nothing here names a component type directly. {@link EnchantmentEffectComponentTypes} is a
	 * registry holder, so touching it from a static initialiser would drag the game's bootstrap into
	 * class loading and make this table untestable in isolation. Both mappings are resolved by
	 * {@link #channelOf} and {@link #valueEffect} instead, when the tooltip is actually drawn.
	 *
	 * <p>Package-private rather than private so the tests can name it; nothing outside this package
	 * should be mapping enchantments to lines.
	 */
	enum Value {
		DAMAGE, KNOCKBACK, PROTECTION, SMASH_DAMAGE, WIND_BURST_KNOCKBACK, WIND_BURST_RADIUS
	}

	private static final String SHARPNESS = "minecraft:sharpness";

	/**
	 * Enchantment identifier to the lines it earns. The four damage enchantments share a channel but
	 * answer to different targets, so they get their own rows; knockback and punch share one.
	 *
	 * <p>An enchantment usually earns one line. Wind burst earns two, because it both multiplies the
	 * knockback it deals and sets the radius of the blast it causes, and the two answers are not
	 * quantities of the same thing.
	 */
	private static final Map<String, List<Category>> CATEGORIES = Map.ofEntries(
			Map.entry("minecraft:smite", List.of(plain(Value.DAMAGE, "tooltip.limitless.damage.undead"))),
			Map.entry("minecraft:bane_of_arthropods", List.of(plain(Value.DAMAGE, "tooltip.limitless.damage.arthropods"))),
			Map.entry("minecraft:impaling", List.of(plain(Value.DAMAGE, "tooltip.limitless.damage.aquatic"))),
			Map.entry("minecraft:power", List.of(plain(Value.DAMAGE, "tooltip.limitless.damage.projectile"))),
			Map.entry("minecraft:knockback", List.of(plain(Value.KNOCKBACK, "tooltip.limitless.knockback"))),
			Map.entry("minecraft:punch", List.of(plain(Value.KNOCKBACK, "tooltip.limitless.knockback"))),
			Map.entry("minecraft:protection", List.of(plain(Value.PROTECTION, "tooltip.limitless.protection"))),
			Map.entry("minecraft:fire_protection", List.of(plain(Value.PROTECTION, "tooltip.limitless.protection.fire"))),
			Map.entry("minecraft:blast_protection", List.of(plain(Value.PROTECTION, "tooltip.limitless.protection.blast"))),
			Map.entry("minecraft:projectile_protection", List.of(plain(Value.PROTECTION, "tooltip.limitless.protection.projectile"))),
			Map.entry("minecraft:feather_falling", List.of(plain(Value.PROTECTION, "tooltip.limitless.protection.fall"))),
			Map.entry("minecraft:density", List.of(new Category(Value.SMASH_DAMAGE, "tooltip.limitless.smash_damage", Style.FIXED))),
			Map.entry("minecraft:wind_burst", List.of(
					new Category(Value.WIND_BURST_KNOCKBACK, "tooltip.limitless.self_knockback", Style.FIXED),
					new Category(Value.WIND_BURST_RADIUS, "tooltip.limitless.impact_radius", Style.FIXED))));

	private EnchantmentStats() {
	}

	private static Category plain(Value value, String key) {
		return new Category(value, key, Style.PLAIN);
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

			List<Category> categories = CATEGORIES.get(id);

			if (categories == null) {
				continue;
			}

			Enchantment enchantment = entry.getKey().value();
			int level = entry.getIntValue();

			for (Category category : categories) {
				if (isEnabled(configuration, channelOf(category.value()))) {
					merge(merged, enchantment, level, category);
				}
			}
		}

		List<Stat> stats = new ArrayList<>(merged.size());

		merged.forEach((line, value) -> {
			if (value != 0.0F) {
				stats.add(new Stat(line.channel(), line.key(), value, line.style()));
			}
		});

		return stats;
	}

	/** Adds one line, or leaves the map alone when the enchantment says nothing at this level. */
	private static void merge(Map<Line, Float> merged, Enchantment enchantment, int level, Category category) {
		Line line = new Line(channelOf(category.value()), category.key(), category.style());

		switch (category.value()) {
			case WIND_BURST_KNOCKBACK -> windBurst(enchantment)
					.flatMap(ExplodeEnchantmentEffect::knockbackMultiplier)
					.ifPresent(value -> add(merged, line, value.getValue(level)));
			case WIND_BURST_RADIUS -> windBurst(enchantment)
					.ifPresent(explode -> add(merged, line, explode.radius().getValue(level)));
			default -> add(merged, line, sum(enchantment, valueEffect(category.value()), level));
		}
	}

	/**
	 * The configuration switch governing a source. Density answers as damage and wind burst as both,
	 * because a knockback multiplier and an explosion radius are damage lines wearing two different
	 * kinds of number.
	 */
	static Channel channelOf(Value value) {
		return switch (value) {
			case DAMAGE, SMASH_DAMAGE, WIND_BURST_RADIUS -> Channel.DAMAGE;
			case KNOCKBACK, WIND_BURST_KNOCKBACK -> Channel.KNOCKBACK;
			case PROTECTION -> Channel.PROTECTION;
		};
	}

	/** The channel a source adds its values to. The wind burst pair reads no channel at all. */
	private static ComponentType<List<EnchantmentEffectEntry<EnchantmentValueEffect>>> valueEffect(Value value) {
		return switch (value) {
			case DAMAGE -> EnchantmentEffectComponentTypes.DAMAGE;
			case KNOCKBACK -> EnchantmentEffectComponentTypes.KNOCKBACK;
			case PROTECTION -> EnchantmentEffectComponentTypes.DAMAGE_PROTECTION;
			// Density: a coefficient added per block fallen, never a flat bonus.
			case SMASH_DAMAGE -> EnchantmentEffectComponentTypes.SMASH_DAMAGE_PER_FALLEN_BLOCK;
			case WIND_BURST_KNOCKBACK, WIND_BURST_RADIUS ->
					throw new IllegalStateException(value + " reads no value-effect channel");
		};
	}

	/**
	 * The explosion wind burst causes, found by type rather than by position.
	 *
	 * <p>{@code post_attack} is a list and anything may sit in it, so taking the first element would
	 * report whichever effect happened to be declared first — which is right today and silently wrong
	 * the day it is not, with nothing raised to say so. Only the first explosion is returned: an
	 * enchantment causing two would have no single radius to report.
	 */
	private static Optional<ExplodeEnchantmentEffect> windBurst(Enchantment enchantment) {
		for (TargetedEnchantmentEffect<EnchantmentEntityEffect> effect
				: enchantment.getEffect(EnchantmentEffectComponentTypes.POST_ATTACK)) {
			if (effect.effect() instanceof ExplodeEnchantmentEffect explode) {
				return Optional.of(explode);
			}
		}

		return Optional.empty();
	}

	private static void add(Map<Line, Float> merged, Line line, float value) {
		if (value != 0.0F) {
			merged.merge(line, value, Float::sum);
		}
	}

	private static float sum(Enchantment enchantment,
			ComponentType<List<EnchantmentEffectEntry<EnchantmentValueEffect>>> channel,
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

	/** The number of a line, printed the way that line wants it. */
	public static String render(Stat stat) {
		return stat.style() == Style.FIXED ? formatFixed(stat.value()) : format(stat.value());
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

	/**
	 * Two decimals always.
	 *
	 * <p>Deliberately kept out of {@link #format}: that one prints whole values bare, and the lines
	 * already in front of players have to keep doing so — knockback III stays {@code +3}, not
	 * {@code +3.00}. Widening it for the sake of the mace lines would change every other tooltip in
	 * the game.
	 *
	 * <p>The pattern is {@code 0.00} rather than {@code #.00}: {@code #} may drop the integer part
	 * entirely when it is zero, which would print the radius of nothing as {@code .00}.
	 */
	public static String formatFixed(float value) {
		return new java.text.DecimalFormat("0.00", java.text.DecimalFormatSymbols.getInstance(Locale.ROOT)).format(value);
	}
}