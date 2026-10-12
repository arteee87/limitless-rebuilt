package limitless.mixin;

import limitless.config.Configuration;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Anvil behaviour.
 *
 * <p>1.19.3 patched this class the same way; the method the level limit targeted is still
 * {@code updateResult} (it is merely declared abstract in {@code ForgingScreenHandler} now, so it
 * has no Yarn name of its own).
 */
@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin {
	private static final int VANILLA_MINIMUM_COST = 40;

	/**
	 * Vanilla doubles the repair cost every time the anvil is used; the mod keeps it constant, which
	 * covers both "constant repair cost" and "anvil cost does not grow".
	 */
	@Inject(method = "getNextCost", at = @At("HEAD"), cancellable = true)
	private static void limitless_getNextCost(int cost, CallbackInfoReturnable<Integer> info) {
		if (Configuration.instance.anvil.fixedCost) {
			info.setReturnValue(0);
		}
	}

	/**
	 * Allows applying an enchantment the item is not normally a target for.
	 *
	 * <p>This must not be a {@code @Redirect}: fabric-item-api already redirects the very same call
	 * to {@code ItemStack#canBeEnchantedWith}, and two redirectors cannot own one instruction —
	 * whichever of the two is applied second finds nothing and aborts the transformation.
	 * {@code @ModifyExpressionValue} rewrites the result of the call instead, which composes with
	 * whatever the other mod did to it.
	 */
	@ModifyExpressionValue(
			method = "updateResult",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/enchantment/Enchantment;isAcceptableItem(Lnet/minecraft/item/ItemStack;)Z"
			)
	)
	private boolean allowIncompatibleItem(boolean acceptable) {
		return acceptable || Configuration.instance.anvil.mergeIncompatible;
	}

	/** Allows applying an enchantment that conflicts with one already on the item. */
	@Redirect(
			method = "updateResult",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/enchantment/Enchantment;canBeCombined"
							+ "(Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/registry/entry/RegistryEntry;)Z"
			),
			require = 0
	)
	private static boolean allowConflictingEnchantment(RegistryEntry<Enchantment> first, RegistryEntry<Enchantment> second) {
		return Enchantment.canBeCombined(first, second) || Configuration.instance.anvil.mergeConflicts;
	}

	/**
	 * Vanilla refuses to produce a result once the cost reaches forty levels, and caps a rename only
	 * operation at thirty nine; {@code levelLimit} replaces that threshold.
	 *
	 * <p>{@code updateResult} reads the cost property exactly twice, in the two comparisons against
	 * forty, so clamping the value it returns turns "at least forty" into "at least max(forty,
	 * levelLimit)" — which is what raising the limit means. 1.19.3 replaced the literal forty with
	 * a constant, but it also rewrote the unrelated "stacked items cost at least forty levels" rule,
	 * which made the anvil refuse any stack of more than one item; that third occurrence is left
	 * alone here.
	 */
	@Redirect(
			method = "updateResult",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/Property;get()I")
	)
	private int applyLevelLimit(Property property) {
		int cost = property.get();
		return Math.min(cost, Math.max(Configuration.instance.anvil.levelLimit, VANILLA_MINIMUM_COST));
	}
}
