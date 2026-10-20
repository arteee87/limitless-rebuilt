package limitless_rebuilt.mixin;

import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Removes the {@code Math.min(level, 255)} clamp of the enchantment builder.
 *
 * <p>Companion of {@link ItemEnchantmentsComponentMixin}: without this the level is truncated while
 * the component is being assembled, even after the codec and the constructor stopped rejecting it.
 *
 * @author auoeke
 * @reason the mod raises the enchantment level limit beyond 255
 */
@Mixin(ItemEnchantments.Mutable.class)
public abstract class ItemEnchantmentsComponentBuilderMixin {
	@ModifyConstant(method = {"set", "upgrade"}, constant = @Constant(intValue = 255))
	private int limitless_builderLimit(int original) {
		return Integer.MAX_VALUE;
	}
}