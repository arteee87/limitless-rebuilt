package limitless_rebuilt.mixin;

import limitless_rebuilt.config.Configuration;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EnchantableComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes enchanted books carry the {@code enchantable} component.
 *
 * <p>1.19.3 overrode {@code EnchantedBookItem#getEnchantability()} to return that of a plain book,
 * which is how a book could be enchanted at a table. 1.21.4 has no such method: the enchantability
 * lives in a component, and {@code ItemStack} runs {@link Item#postProcessComponents(ItemStack)} for
 * every stack it creates — the same hook vanilla uses for potions and buckets.
 *
 * <p>The book is recognised by its {@code stored_enchantments} component instead of by comparing it
 * with {@code Items.ENCHANTED_BOOK}: {@code postProcessComponents} runs from the {@code ItemStack}
 * constructor, which can happen while {@code Items} itself is still initialising, and touching that
 * class from here would re-enter its static initialiser. In 1.21.4 the enchanted book is the only
 * item whose settings carry that component.
 */
@Mixin(Item.class)
public abstract class ItemMixin {
	@Inject(method = "postProcessComponents", at = @At("HEAD"))
	private void limitless_postProcessComponents(ItemStack stack, CallbackInfo info) {
		if (stack.contains(DataComponentTypes.STORED_ENCHANTMENTS)
				&& !stack.contains(DataComponentTypes.ENCHANTABLE)
				&& Configuration.instance.enchantment.reenchanting.allowEnchantedBooks()) {
			stack.set(DataComponentTypes.ENCHANTABLE, new EnchantableComponent(1));
		}
	}
}