package limitless_rebuilt.mixin;

import limitless_rebuilt.config.Configuration;
import limitless_rebuilt.config.enchantment.entry.normalization.EnchantmentNormalizationEntry;
import limitless_rebuilt.enchantment.ExperienceUtil;
import net.minecraft.world.entity.player.Player;
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
@Mixin(Player.class)
public abstract class PlayerEntityMixin {
	@Redirect(
			method = "onEnchantmentPerformed",
			at = @At(
					value = "FIELD",
					target = "Lnet/minecraft/world/entity/player/Player;experienceLevel:I",
					ordinal = 1
			)
	)
	private void normalizeCost(Player player, int levels) {
		EnchantmentNormalizationEntry normalization = Configuration.instance.enchantment.normalization;

		if (normalization.enabled && player.experienceLevel > normalization.offset) {
			ExperienceUtil.addExperienceLevelsRelatively(player, normalization.offset, levels - player.experienceLevel);
		} else {
			player.experienceLevel = levels;
		}
	}
}