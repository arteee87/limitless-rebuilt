package limitless_rebuilt.enchantment;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import limitless_rebuilt.config.Configuration;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.EnchantmentTags;

/**
 * Enchantment merging used by the enchanting table.
 *
 * <p>The 1.19.3 implementation read and wrote the {@code StoredEnchantments} NBT list and truncated
 * the level to a {@code short} on the way; 1.21.11 keeps the same data in
 * {@link ItemEnchantmentsComponent}, where the level is an {@code int} and never truncates.
 */
public final class EnchantmentUtil {
	/**
	 * How many levels above the minimum of an enchantment are searched.
	 *
	 * <p>{@code getPossibleEntries} used to be a plain loop from {@code getMaxLevel()} down to the
	 * minimum, which with an unlimited maximum is about two billion iterations. 1.19.3 could afford
	 * an upward search because its power curve was an array lookup clamped to its last entry; the
	 * 1.21.4 curve is {@code base + perLevel * (level - 1)} in plain int arithmetic, so it wraps
	 * around on huge levels and stops being monotonic. The cap keeps a flat curve — whose minimum
	 * power never exceeds the table — from running to the ceiling. Reaching the cap means an
	 * enchantment whose power does not grow, and those keep their vanilla maximum anyway.
	 */
	private static final int MAX_LEVEL_SEARCH = 1024;
	private EnchantmentUtil() {
	}
	/**
	 * Adds the highest level of {@code entry} that the given enchanting power can reach.
	 *
	 * @return whether a level was added
	 */
	public static boolean highestSuitableLevel(
			int power,
			RegistryEntry<Enchantment> entry,
			List<EnchantmentLevelEntry> out) {
		Enchantment enchantment = entry.value();
		int minLevel = enchantment.getMinLevel();
		int last = Math.min(enchantment.getMaxLevel(), minLevel + MAX_LEVEL_SEARCH);
		int found = 0;
		boolean any = false;
		for (int level = minLevel; level <= last; level++) {
			int minPower = enchantment.getMinPower(level);
			if (minPower > power) {
				// the curve grew past what the table offers, so nothing above matches either
				break;
			}
			if (minPower <= power && power <= enchantment.getMaxPower(level)) {
				found = level;
				any = true;
			}
		}
		if (any) {
			out.add(new EnchantmentLevelEntry(entry, found));
		}
		return any;
	}
	/**
	 * Adds an enchantment to a stack, merging it into an existing one instead of replacing it.
	 *
	 * @param mergeConflicts whether an enchantment conflicting with a present one is still applied
	 * @return whether the enchantment was applied
	 */
	public static boolean mergeEnchantment(
			ItemStack stack,
			RegistryEntry<Enchantment> enchantment,
			int level,
			boolean mergeConflicts) {
		ItemEnchantmentsComponent.Builder builder =
				new ItemEnchantmentsComponent.Builder(EnchantmentHelper.getEnchantments(stack));
		if (!mergeConflicts) {
			for (RegistryEntry<Enchantment> present : builder.getEnchantments()) {
				if (!present.equals(enchantment) && !Enchantment.canBeCombined(enchantment, present)) {
					return false;
				}
			}
		}
		int current = builder.getLevel(enchantment);
		if (current == level && current != 0) {
			builder.set(enchantment, Math.min(current + 1, maxLevel(enchantment)));
		} else {
			builder.set(enchantment, Math.max(current, level));
		}
		// EnchantmentHelper.set picks STORED_ENCHANTMENTS for books and ENCHANTMENTS otherwise
		EnchantmentHelper.set(stack, builder.build());
		return true;
	}
	private static int maxLevel(RegistryEntry<Enchantment> enchantment) {
		EnchantmentLimits.Limit limit = EnchantmentLimits.get(enchantment.value());
		return limit == null ? enchantment.value().getMaxLevel() : limit.maxLevel();
	}
	/** Identifier of an enchantment, for the debug log. */
	public static String identifier(RegistryEntry<Enchantment> enchantment) {
		return enchantment.getKey()
				.map(key -> key.getValue().toString())
				.orElse("unknown");
	}
	/**
	 * How many enchantments the enchanting table may offer at all, i.e. the size of
	 * {@code #minecraft:in_enchanting_table}. Zero here means an empty table whatever the limits are.
	 */
	public static int candidateCount(DynamicRegistryManager registries) {
		if (registries == null) {
			return -1;
		}
		return registries.getOrThrow(RegistryKeys.ENCHANTMENT)
				.getOptional(EnchantmentTags.IN_ENCHANTING_TABLE)
				.map(RegistryEntryList::stream)
				.map(stream -> (int) stream.count())
				.orElse(0);
	}
	/**
	 * Whether the mod treats this stack as enchantable at all.
	 *
	 * <p>Two readers have to agree on that answer, and until 1.21.11 they did not. The enchanting table
	 * asks {@code ItemStack#isEnchantable()}, while {@link EnchantmentUtil#candidateCount} is reached
	 * only after our own level formula has already answered, and that formula reads the
	 * {@code enchantable} component directly rather than calling the method. An enchanted book carries
	 * neither, so patching one of the two left the other returning zero — and a slot the table skips.
	 *
	 * <p>1.21.4 avoided the question by adding the component to every enchanted book, from a hook Mojang
	 * has since deleted. One predicate replaces both the component and the method: a stack qualifies
	 * when it carries {@code enchantable}, or when it is an enchanted book and the flag is on. The flag
	 * is read through {@code ReenchantingConfiguration#allowEnchantedBooks()}, which folds in
	 * {@code reenchanting.enabled}, so both halves are honoured here.
	 */
	public static boolean isEnchantable(ItemStack stack) {
		if (stack.contains(DataComponentTypes.ENCHANTABLE)) {
			return true;
		}

		return Configuration.instance.enchantment.reenchanting.allowEnchantedBooks()
				&& stack.contains(DataComponentTypes.STORED_ENCHANTMENTS);
	}

	/** Every enchantment a stack carries, keyed by identifier, for logging and for the GUI. */
	public static Map<String, Integer> levels(ItemStack stack) {
		Map<String, Integer> levels = new LinkedHashMap<>();
		for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry :
				EnchantmentHelper.getEnchantments(stack).getEnchantmentEntries()) {
			RegistryEntry<Enchantment> enchantment = entry.getKey();
			levels.put(
					enchantment.getKey().map(key -> key.getValue().toString()).orElse("unknown"),
					entry.getIntValue());
		}
		return levels;
	}
	/** Renders entries as {@code [id=level, ...]}; the first few only, the rest is noise in a log. */
	public static String describe(List<EnchantmentLevelEntry> entries) {
		if (entries.isEmpty()) {
			return "";
		}

		StringBuilder builder = new StringBuilder(" [");
		int shown = Math.min(entries.size(), 4);

		for (int i = 0; i < shown; i++) {
			if (i > 0) {
				builder.append(", ");
			}

			EnchantmentLevelEntry entry = entries.get(i);
			builder.append(identifier(entry.enchantment())).append('=').append(entry.level());
		}

		if (entries.size() > shown) {
			builder.append(", +").append(entries.size() - shown).append(" more");
		}

		return builder.append(']').toString();
	}
}
