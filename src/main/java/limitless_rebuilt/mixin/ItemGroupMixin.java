package limitless_rebuilt.mixin;

import limitless_rebuilt.enchantment.EnchantmentLimits;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.RegistryWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Creative tab books.
 *
 * <p>Vanilla fills the search tab with one enchanted book per level from the minimum to
 * {@code getMaxLevel()}, which the mod raises to {@link Integer#MAX_VALUE} for most enchantments —
 * that loop would try to add two billion books. 1.19.3 patched the loop with two
 * {@code @ModifyVariable} injections and ended up adding exactly one extra book; here the method is
 * rewritten to list the vanilla range plus a bounded number of levels above it.
 */
@Mixin(ItemGroups.class)
public abstract class ItemGroupMixin {
	private static final int EXTRA_BOOKS = 5;

	/**
 * Lists every book of an enchantment, bounded so an unlimited level does not hang the tab.
 *
 * @author auoeke
 * @reason the vanilla range would run from the minimum level to the configured maximum, which is
 * {@link Integer#MAX_VALUE} for most enchantments
 */
	@Overwrite
	private static void addAllLevelEnchantedBooks(
			ItemGroup.Entries entries,
			RegistryWrapper<Enchantment> registryWrapper,
			ItemGroup.StackVisibility stackVisibility) {
		registryWrapper.streamEntries().forEach(candidate -> {
			Enchantment enchantment = candidate.value();
			int minLevel = enchantment.getMinLevel();
			int vanillaMaxLevel = Math.max(EnchantmentLimits.originalMaxLevel(enchantment), minLevel);
			int maxLevel = Math.min(enchantment.getMaxLevel(), vanillaMaxLevel + EXTRA_BOOKS);

			for (int level = minLevel; level <= maxLevel; level++) {
				entries.add(
						EnchantmentHelper.getEnchantedBookWith(new EnchantmentLevelEntry(candidate, level)),
						stackVisibility);
			}
		});
	}
}
