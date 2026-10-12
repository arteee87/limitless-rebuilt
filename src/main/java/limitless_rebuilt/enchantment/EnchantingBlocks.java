package limitless_rebuilt.enchantment;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import limitless_rebuilt.LimitlessLogger;
import limitless_rebuilt.config.Configuration;
import limitless_rebuilt.config.enchantment.EnchantingBlockConfiguration;
import limitless_rebuilt.config.enchantment.entry.radius.HorizontalRadius;
import limitless_rebuilt.config.enchantment.entry.radius.VerticalRadius;
import net.minecraft.block.Block;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EnchantableComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Bookshelf scanning and the experience level an enchanting table asks for.
 *
 * <p>Vanilla counts the fifteen bookcases around a table and clamps that count to fifteen, which
 * caps the power and therefore the level. Here every block of {@code #c:enchanting_blocks} (and
 * {@code #c:bookshelves}) contributes {@link EnchantingBlockConfiguration#DEFAULT_POWER}, blocks
 * listed in the configuration contribute their own amount, and the scan covers a configurable box.
 */
public final class EnchantingBlocks {
	private static final Map<Block, Float> WHITELIST = new IdentityHashMap<>();
	private static final Set<Block> BLACKLIST = Collections.newSetFromMap(new IdentityHashMap<>());

	private EnchantingBlocks() {
	}

	/** Rebuilds the block lookup after a configuration reload. */
	public static void reload() {
		EnchantingBlockConfiguration configuration = Configuration.instance.enchantment.enchantingBlocks;

		synchronized (WHITELIST) {
			WHITELIST.clear();
			BLACKLIST.clear();

			for (Map.Entry<String, Float> entry : configuration.whitelist.entrySet()) {
				resolve(entry.getKey()).ifPresent(block -> {
					WHITELIST.put(block, entry.getValue());

					if (BLACKLIST.contains(block)) {
						BLACKLIST.remove(block);
						LimitlessLogger.warn(
								"Block with identifier \"%s\" was found in whitelist and blacklist; whitelist takes precedence."
										.formatted(entry.getKey()));
					}
				});
			}

			for (String key : configuration.blacklist) {
				resolve(key).ifPresent(BLACKLIST::add);
			}
		}
	}

	private static java.util.Optional<Block> resolve(String key) {
		Identifier identifier = Identifier.tryParse(key);
		RegistryKey<Block> registryKey = identifier == null
				? null
				: RegistryKey.of(net.minecraft.registry.RegistryKeys.BLOCK, identifier);
		java.util.Optional<Block> block = registryKey == null
				? java.util.Optional.empty()
				: Registries.BLOCK.getOptionalValue(registryKey);

		block.ifPresentOrElse(
				ignored -> {
				},
				() -> LimitlessLogger.warn("A block with identifier \"%s\" is not registered.".formatted(key)));

		return block;
	}

	public static float enchantingPower(Block block) {
		synchronized (WHITELIST) {
			Float power = WHITELIST.get(block);

			if (power != null) {
				return power;
			}

			if (BLACKLIST.contains(block)) {
				return 0.0F;
			}
		}

		return isEnchanting(block) ? EnchantingBlockConfiguration.DEFAULT_POWER : 0.0F;
	}

	private static boolean isEnchanting(Block block) {
		return block.getRegistryEntry().isIn(EnchantingBlockConfiguration.ENCHANTING_BLOCKS)
				|| block.getRegistryEntry().isIn(EnchantingBlockConfiguration.BOOKSHELVES);
	}

	/**
 * Visits every position of the configured box around the table.
 *
 * <p>The same traversal as the 1.19.3 mod: for each horizontal distance the square ring is walked in
 * both axis orders, and the transposed position is skipped on the corners because the other
 * direction already visits them — counted twice, a corner bookshelf would add {@link
 * EnchantingBlockConfiguration#DEFAULT_POWER} for nothing.
 */
public static void forEachInRange(World world, BlockPos center, BiConsumer<Block, BlockPos> visitor) {
	HorizontalRadius horizontal = Configuration.instance.enchantment.enchantingBlocks.radius.xz;
	VerticalRadius vertical = Configuration.instance.enchantment.enchantingBlocks.radius.y;

	for (int direction = -1; direction <= 1; direction += 2) {
		for (int y = vertical.min; y <= vertical.max; y++) {
			for (int radius = horizontal.min; radius <= horizontal.max; radius++) {
				int end = direction * radius;

				for (int distance = -radius; distance <= radius; distance++) {
					int displacement = direction * distance;
					BlockPos first = center.add(displacement, y, end);
					visitor.accept(world.getBlockState(first).getBlock(), first);

					if (distance != radius && distance != -radius) {
						BlockPos second = center.add(end, y, displacement);
						visitor.accept(world.getBlockState(second).getBlock(), second);
					}
				}
			}
		}
	}
}

/** Total power of every contributing block around the table. */
public static float countEnchantingPower(World world, BlockPos center) {
		float[] power = {0.0F};

		forEachInRange(world, center, (block, offset) -> power[0] += enchantingPower(block));

		return power[0];
	}

	private static float power(World world, BlockPos center, int dx, int dy, int dz) {
		return enchantingPower(world.getBlockState(center.add(dx, dy, dz)).getBlock());
	}

	/**
	 * Highest power the table may use, which is what the two ceiling settings mean.
	 *
	 * <p>{@code maxBlocks} has no meaning on its own: the original mod substituted it for the
	 * constant {@code 15} in vanilla's bookshelf counter, a method it had itself already replaced on
	 * the enchanting table. What survives of that intent here is a ceiling on the accumulated power,
	 * with a bookshelf worth {@link EnchantingBlockConfiguration#DEFAULT_POWER} — at the defaults
	 * {@code 512 * 2.0} is exactly {@code maxPower}, so both settings agree.
	 */
	public static float maxPower() {
		EnchantingBlockConfiguration configuration = Configuration.instance.enchantment.enchantingBlocks;

		// a negative ceiling would make the nextInt bound in the level formula non positive
		return Math.max(0.0F, Math.min(
				configuration.maxPower,
				Math.max(0, configuration.maxBlocks) * EnchantingBlockConfiguration.DEFAULT_POWER));
	}

	/**
	 * Experience level the table asks for, matching the 1.19.3 mod rather than the 1.21.4 vanilla
	 * one: the accumulated power is used directly instead of being a bookshelf count.
	 */
	public static int calculateRequiredExperienceLevel(Random random, int slotIndex, float enchantingPower, ItemStack stack) {
		EnchantableComponent enchantable = stack.get(DataComponentTypes.ENCHANTABLE);

		if (enchantable == null) {
			return 0;
		}

		enchantingPower = Math.min(enchantingPower, maxPower());

		float level = (float) (random.nextInt(8) + 1) + enchantingPower / 4.0F
				+ (float) random.nextInt((int) enchantingPower / 2 + 1);

		return switch (slotIndex) {
			case 0 -> (int) Math.max(level / 3.0F, 1.0F);
			case 1 -> (int) (level * 2.0F / 3.0F + 1.0F);
			default -> (int) Math.max(level, enchantingPower);
		};
	}
}