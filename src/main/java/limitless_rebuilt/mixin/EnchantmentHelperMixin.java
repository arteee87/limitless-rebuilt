package limitless_rebuilt.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import limitless_rebuilt.config.Configuration;
import limitless_rebuilt.config.enchantment.entry.EnchantingConflicts;
import limitless_rebuilt.enchantment.EnchantmentUtil;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Enchantment table candidates.
 *
 * <p>1.19.3 rewrote the body of {@code getPossibleEntries} so that it walked the levels upwards and
 * kept the highest one that fitted; 1.21.4 keeps the same result but walks downwards from
 * {@code getMaxLevel()}, which the mod raises to {@link Integer#MAX_VALUE}. That loop is what this
 * overwrite replaces — see {@link EnchantmentUtil#highestSuitableLevel} for the bounds.
 *
 * <p>What is left here is the re-enchanting of enchanted books: vanilla only treats a plain
 * {@link Items#BOOK} as a wildcard target.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {
	@Overwrite
	public static List<EnchantmentLevelEntry> getPossibleEntries(
			int level,
			ItemStack stack,
			Stream<RegistryEntry<Enchantment>> possibleEnchantments) {
		List<EnchantmentLevelEntry> entries = new ArrayList<>();
		boolean book = stack.isOf(Items.BOOK);

		possibleEnchantments
				.filter(entry -> entry.value().isPrimaryItem(stack) || book)
				.forEach(entry -> EnchantmentUtil.highestSuitableLevel(level, entry, entries));

		return entries;
	}

	@Redirect(
			method = "getPossibleEntries",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isOf(Lnet/minecraft/item/Item;)Z")
	)
	private static boolean treatEnchantedBookAsBook(ItemStack stack, Item item) {
		if (item == Items.BOOK
				&& Configuration.instance.enchantment.reenchanting.allowEnchantedBooks()
				&& stack.isOf(Items.ENCHANTED_BOOK)) {
			return true;
		}

		return stack.isOf(item);
	}

	/**
	 * Vanilla drops every enchantment conflicting with the one it just picked; with
	 * {@code conflicts.generate} only the same enchantment is dropped, so a lap can offer several
	 * mutually exclusive enchantments.
	 */
	@Redirect(
			method = "generateEnchantments",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/enchantment/EnchantmentHelper;removeConflicts(Ljava/util/List;Lnet/minecraft/enchantment/EnchantmentLevelEntry;)V"
			)
	)
	private static void keepConflicts(List<EnchantmentLevelEntry> possibleEntries, EnchantmentLevelEntry picked) {
		EnchantingConflicts conflicts = Configuration.instance.enchantment.conflicts;

		if (conflicts.generate) {
			possibleEntries.removeIf(entry -> !entry.enchantment.equals(picked.enchantment));
		} else {
			EnchantmentHelper.removeConflicts(possibleEntries, picked);
		}
	}
}