package limitless.mixin;

import limitless.config.Configuration;
import limitless.config.enchantment.entry.normalization.EnchantmentNormalizationEntry;
import limitless.enchantment.ExperienceUtil;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes an enchanting table cost the same number of levels a player of {@code offset} would pay.
 *
 * <p>Vanilla writes the resulting level straight into {@code experienceLevel}, dropping the player
 * from wherever they were to exactly that level; the mod instead subtracts the same amount of
 * experience, so a player at level 300 pays like a player at level 30.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
	@Redirect(
			method = "applyEnchantmentCosts",
			at = @At(
					value = "FIELD",
					target = "Lnet/minecraft/entity/player/PlayerEntity;experienceLevel:I",
					ordinal = 1
			)
	)
	private void normalizeCost(PlayerEntity player, int levels) {
		EnchantmentNormalizationEntry normalization = Configuration.instance.enchantment.normalization;

		if (normalization.enabled && player.experienceLevel > normalization.offset) {
			ExperienceUtil.addExperienceLevelsRelatively(player, normalization.offset, levels - player.experienceLevel);
		} else {
			player.experienceLevel = levels;
		}
	}
}