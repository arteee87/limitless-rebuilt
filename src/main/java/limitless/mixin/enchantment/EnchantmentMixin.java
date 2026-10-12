package limitless.mixin.enchantment;

import limitless.config.Configuration;
import limitless.enchantment.EnchantmentLimits;
import net.minecraft.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the level ceiling.
 *
 * <p>1.19.3 rewrote the bytecode of this class at load time so that {@code getMaxLevel()} returned
 * the configured value and {@code getMaxPower(int)} always returned {@link Integer#MAX_VALUE}; the
 * same result is reached here with two injections.
 *
 * <p>{@code Enchantment} is a {@code final record} in 1.21.4, so the per instance state lives in
 * {@link EnchantmentLimits} rather than in {@code @Unique} fields.
 */
@Mixin(Enchantment.class)
public abstract class EnchantmentMixin {
	@Inject(method = "getMaxLevel", at = @At("RETURN"), cancellable = true)
	private void limitless_getMaxLevel(CallbackInfoReturnable<Integer> info) {
		EnchantmentLimits.Limit limit = EnchantmentLimits.get((Enchantment) (Object) this);

		if (limit == null) {
			return;
		}

		info.setReturnValue(limit.useGlobalMaxLevel()
				? Configuration.instance.enchantment.globalMaxLevel
				: limit.maxLevel());
	}

	/**
	 * Without this every level above the last vanilla one would be rejected by the
	 * {@code minPower <= power <= maxPower} window.
	 */
	@Inject(method = "getMaxPower", at = @At("RETURN"), cancellable = true)
	private void limitless_getMaxPower(int level, CallbackInfoReturnable<Integer> info) {
		info.setReturnValue(Integer.MAX_VALUE);
	}
}