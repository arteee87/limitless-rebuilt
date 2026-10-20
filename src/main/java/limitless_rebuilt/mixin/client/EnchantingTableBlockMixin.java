package limitless_rebuilt.mixin.client;

import limitless_rebuilt.config.Configuration;
import limitless_rebuilt.config.enchantment.entry.EnchantmentParticleConfiguration;
import limitless_rebuilt.config.enchantment.entry.radius.HorizontalRadius;
import limitless_rebuilt.config.enchantment.entry.radius.VerticalRadius;
import limitless_rebuilt.enchantment.EnchantingBlocks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enchantment particles.
 *
 * <p>Vanilla spawns them at fifteen fixed offsets around the table; the mod draws them over the
 * configured radius for every block that contributes power, so the visual matches what the server
 * counts. The vanilla body is cancelled, which also skips its {@code super} call.
 */
@Environment(EnvType.CLIENT)
@Mixin(EnchantingTableBlock.class)
public abstract class EnchantingTableBlockMixin {
	@Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
	private void limitless_expandParticles(
			BlockState state,
			Level world,
			BlockPos tablePos,
			RandomSource random,
			CallbackInfo info) {
		info.cancel();

		Configuration configuration = Configuration.instance;
		EnchantmentParticleConfiguration particles = configuration.enchantment.particles;

		if (!particles.enabled) {
			return;
		}

		HorizontalRadius horizontal = particles.inherit
				? configuration.enchantment.enchantingBlocks.radius.xz
				: particles.radius.xz;
		VerticalRadius vertical = particles.inherit
				? configuration.enchantment.enchantingBlocks.radius.y
				: particles.radius.y;

		for (int direction = -1; direction <= 1; direction += 2) {
			for (int y = vertical.min; y <= vertical.max; y++) {
				for (int radius = horizontal.min; radius <= horizontal.max; radius++) {
					int end = direction * radius;

					if (random.nextInt(16) != 0) {
						continue;
					}

					for (int distance = -radius; distance <= radius; distance++) {
						int displacement = direction * distance;

						if (hasPower(world, tablePos, displacement, y, end)) {
							particle(world, tablePos, random, displacement, y, end);
						}

						if (distance != -radius && distance != radius && hasPower(world, tablePos, end, y, displacement)) {
							particle(world, tablePos, random, end, y, displacement);
						}
					}
				}
			}
		}
	}

	private static boolean hasPower(Level world, BlockPos tablePos, int dx, int dy, int dz) {
		return EnchantingBlocks.enchantingPower(world.getBlockState(tablePos.offset(dx, dy, dz)).getBlock()) > 0.0F;
	}

	private static void particle(Level world, BlockPos tablePos, RandomSource random, int dx, int dy, int dz) {
		world.addParticle(
				ParticleTypes.ENCHANT,
				tablePos.getX() + 0.5,
				tablePos.getY() + 2.0,
				tablePos.getZ() + 0.5,
				dx + random.nextFloat() - 0.5,
				dy - random.nextFloat() - 1.0F,
				dz + random.nextFloat() - 0.5);
	}
}