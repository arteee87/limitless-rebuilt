package limitless_rebuilt.mixin;

import limitless_rebuilt.enchantment.EnchantmentLimits;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
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
@Mixin(CreativeModeTabs.class)
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
	private static void generateEnchantmentBookTypesAllLevels(
			CreativeModeTab.Output entries,
			HolderLookup<Enchantment> registryWrapper,
			CreativeModeTab.TabVisibility stackVisibility) {
		registryWrapper.listElements().forEach(candidate -> {
			Enchantment enchantment = candidate.value();
			int minLevel = enchantment.getMinLevel();
			int vanillaMaxLevel = Math.max(EnchantmentLimits.originalMaxLevel(enchantment), minLevel);
			int maxLevel = Math.min(enchantment.getMaxLevel(), vanillaMaxLevel + EXTRA_BOOKS);

			for (int level = minLevel; level <= maxLevel; level++) {
				entries.accept(
						EnchantmentHelper.createBook(new EnchantmentInstance(candidate, level)),
						stackVisibility);
			}
		});
	}
}
