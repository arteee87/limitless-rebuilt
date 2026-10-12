package limitless_rebuilt.mixin;

import limitless_rebuilt.enchantment.EnchantmentLimits;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.loot.function.EnchantRandomlyLootFunction;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps enchanted loot inside the vanilla level range.
 *
 * <p>Otherwise a chest or a structure that rolls {@code add_random_enchantment} would produce
 * level 2147483647 books, since the function picks a level between the minimum and
 * {@code getMaxLevel()}.
 */
@Mixin(EnchantRandomlyLootFunction.class)
public abstract class EnchantRandomlyLootFunctionMixin {
	@Redirect(
			method = "addEnchantmentToStack",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/enchantment/Enchantment;getMaxLevel()I")
	)
	private static int vanillaMaxLevel(Enchantment enchantment) {
		return EnchantmentLimits.originalMaxLevel(enchantment);
	}
}
