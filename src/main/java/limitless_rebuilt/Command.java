package limitless_rebuilt;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.ArrayList;
import limitless_rebuilt.enchantment.EnchantingBlocks;
import limitless_rebuilt.config.enchantment.EnchantingBlockConfiguration;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import limitless_rebuilt.config.Configuration;
import limitless_rebuilt.config.enchantment.entry.EnchantmentTooltipConfiguration;
import limitless_rebuilt.enchantment.EnchantmentLimits;
import limitless_rebuilt.enchantment.EnchantmentStats;
import limitless_rebuilt.enchantment.EnchantmentUtil;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.block.Block;

public enum Command implements CommandRegistrationCallback {
	INSTANCE;

	private static final ResourceKey<Registry<Enchantment>> ENCHANTMENTS = Registries.ENCHANTMENT;
	private static final int[] POWERS = {1, 5, 15, 30, 60};

	@Override
	public void register(
			CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext registryAccess,
			Commands.CommandSelection environment) {
		// 1.21.11 replaced ServerCommandSource.hasPermissionLevel(int) with a permission predicate.
		// DefaultPermissions.GAMEMASTERS is the constant for the old operator level 2: PermissionLevel
		// numbers its enum ALL..OWNERS as 0..4, which is exactly the scale hasPermissionLevel used.
		dispatcher.register(Commands.literal("limitless")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.then(Commands.literal("reload")
						.executes(context -> {
							Configuration.reload();

							if (context.getSource().getEntity() instanceof Player player) {
								player.sendSystemMessage(Component.literal("Done."));
							}

							return com.mojang.brigadier.Command.SINGLE_SUCCESS;
						}))
				.then(Commands.literal("debug")
						.executes(context -> diagnose(context.getSource()))
						.then(Commands.literal("stats")
								.executes(context -> stats(context.getSource()))))
				.then(Commands.literal("power")
						.then(Commands.argument("position", BlockPosArgument.blockPos())
								.executes(context -> power(context.getSource(),
										BlockPosArgument.getLoadedBlockPos(context, "position"))))));
	}

	/**
	 * Prints what the enchanting table would offer at several power levels.
	 *
	 * <p>That selection runs entirely on the client, so it cannot be observed from a log of a normal
	 * session; running the same calls here is the only way to tell an empty candidate list from a
	 * power that matches nothing.
	 */
	private static int diagnose(CommandSourceStack source) {
		ServerLevel world = source.getLevel();
		Registry<Enchantment> registry = world.registryAccess().lookupOrThrow(ENCHANTMENTS);

		source.sendSuccess(() -> Component.literal("limitless debug: " + limits(registry)), false);
		source.sendSuccess(() -> Component.literal("registered enchantments: " + registry.keySet().size()
				+ ", limits applied: " + EnchantmentLimits.size()), false);

		List<Holder<Enchantment>> candidates = new ArrayList<>();
		registry.getTagOrEmpty(EnchantmentTags.IN_ENCHANTING_TABLE).forEach(candidates::add);
		source.sendSuccess(() -> Component.literal("#minecraft:in_enchanting_table -> "
				+ candidates.size() + " entries"), false);

		source.sendSuccess(() -> Component.literal("isEnchantable: " + enchantability(candidates)), false);

		// goes through the mixin exactly like the item tooltip does; on a server the name part
		// resolves to its key, so only the numeral is meaningful here
		registry.entrySet().stream()
				.filter(entry -> entry.getKey().identifier().toString().endsWith("sharpness"))
				.findFirst()
				.ifPresent(entry -> source.sendSuccess(() -> Component.literal(
						"level names: 1='%s' 14='%s' 4000='%s'"
								.formatted(
										Enchantment.getFullname(registry.wrapAsHolder(entry.getValue()), 1).getString(),
										Enchantment.getFullname(registry.wrapAsHolder(entry.getValue()), 14).getString(),
										Enchantment.getFullname(registry.wrapAsHolder(entry.getValue()), 4000).getString())), false));

		for (ItemStack stack : List.of(new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.BOOK))) {
			source.sendSuccess(() -> Component.literal("--- " + stack.getItem().getName(stack).getString()
					+ ", enchantable=" + stack.has(net.minecraft.core.component.DataComponents.ENCHANTABLE)
					+ ", isEnchantable=" + stack.isEnchantable()
					+ ", modAccepts=" + EnchantmentUtil.isEnchantable(stack)), false);

			for (int power : POWERS) {
				List<EnchantmentInstance> entries = EnchantmentHelper.getAvailableEnchantmentResults(
						power, stack, candidates.stream());
				source.sendSuccess(() -> Component.literal("  power " + power + " -> "
						+ entries.size() + " entries " + sample(entries)), false);
			}

			for (int power : new int[]{5, 20}) {
				List<EnchantmentInstance> generated = EnchantmentHelper.selectEnchantment(
						RandomSource.create(power), stack, power, candidates.stream());
				source.sendSuccess(() -> Component.literal("  generateEnchantments(power=" + power + ") -> "
						+ generated.size() + " " + sample(generated)), false);
			}
		}

		registry.entrySet().stream()
				.filter(entry -> entry.getKey().identifier().toString().endsWith("sharpness"))
				.findFirst()
				.ifPresent(entry -> {
					Enchantment enchantment = entry.getValue();
					StringBuilder curve = new StringBuilder("  sharpness: minLevel=" + enchantment.getMinLevel()
							+ " maxLevel=" + enchantment.getMaxLevel() + " curve ");

					for (int level = enchantment.getMinLevel(); level <= enchantment.getMinLevel() + 5; level++) {
						curve.append(level).append("->").append(enchantment.getMinCost(level)).append(' ');
					}

					source.sendSuccess(() -> Component.literal(curve.toString()), false);
				});

		source.sendSuccess(() -> Component.literal("anvil levelLimit=" + Configuration.instance.anvil.levelLimit
					+ ", costLimitEnabled=" + Configuration.instance.anvil.costLimitEnabled
					+ ", costLimit=" + Configuration.instance.anvil.costLimit
				+ ", enchantingPower block default=" + net.minecraft.network.chat.Component.literal("see config")
				+ ", maxPower=" + Configuration.instance.enchantment.enchantingBlocks.maxPower
				+ ", maxBlocks=" + Configuration.instance.enchantment.enchantingBlocks.maxBlocks), false);

		return com.mojang.brigadier.Command.SINGLE_SUCCESS;
	}

	/**
	 * Prints the tooltip lines a stack would be given, without needing a client.
	 *
	 * <p>The tooltip renders on the client only, so a slip in how a line is assembled cannot be seen
	 * from here — but everything before the render can, and that is where the faults were: a number
	 * handed to a translation with no placeholder to put it in, a second argument never filled in, two
	 * enchantments that answer to the same line printing twice. Each line is reported with the key it
	 * uses, the number it carries and the argument that fills the second placeholder.
	 */
	private static int stats(CommandSourceStack source) {
		ServerLevel world = source.getLevel();
		Registry<Enchantment> registry = world.registryAccess().lookupOrThrow(ENCHANTMENTS);
		EnchantmentTooltipConfiguration tooltip = Configuration.instance.enchantment.tooltip;

		source.sendSuccess(() -> Component.literal("enabled=" + tooltip.enabled
				+ ", showDamage=" + tooltip.showDamage
				+ ", showKnockback=" + tooltip.showKnockback
				+ ", showProtection=" + tooltip.showProtection), false);

		// punch and knockback share a line on purpose, so the two of them have to come out as one
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		String[] test = {"sharpness", "knockback", "punch", "protection", "feather_falling", "impaling"};
		int[] levels = {17, 10, 9, 14, 24, 20};

		for (int i = 0; i < test.length; i++) {
			String path = test[i];
			int level = levels[i];
			Holder<Enchantment> enchantment = entry(registry, path);

			if (enchantment == null) {
				source.sendSuccess(() -> Component.literal("  missing: " + path), false);
				continue;
			}

			sword.enchant(enchantment, level);
			source.sendSuccess(() -> Component.literal("  " + path + " " + level), false);
		}

		source.sendSuccess(() -> Component.literal("sharpnessBonus=" + EnchantmentStats.sharpnessBonus(sword)), false);

		List<EnchantmentStats.Stat> collected = EnchantmentStats.collect(sword);

		if (collected.isEmpty()) {
			source.sendSuccess(() -> Component.literal("no lines collected"), false);
		}

		for (EnchantmentStats.Stat stat : collected) {
			source.sendSuccess(() -> Component.literal("  " + stat.key()
					+ " style=" + stat.style()
					+ " value=" + EnchantmentStats.render(stat)), false);
		}

		return com.mojang.brigadier.Command.SINGLE_SUCCESS;
	}

	private static Holder<Enchantment> entry(Registry<Enchantment> registry, String path) {
		return registry.get(Identifier.withDefaultNamespace(path)).orElse(null);
	}

	/**
	 * Whether the table would accept these items at all.
	 *
	 * <p>{@code EnchantmentScreenHandler#onContentChanged} empties all three lapis slots and skips the
	 * whole scan when {@code ItemStack#isEnchantable()} answers {@code false}, which leaves an empty
	 * table with no other symptom; a wrong answer here is therefore the difference between "the table
	 * is broken" and "the table is fine".
	 */
	private static String enchantability(List<Holder<Enchantment>> candidates) {
		StringBuilder builder = new StringBuilder();

		for (Item item : List.of(Items.DIAMOND_SWORD, Items.BOOK, Items.DIAMOND_PICKAXE, Items.STICK, Items.DIRT)) {
			if (builder.length() > 0) {
				builder.append("  ");
			}

			builder.append(item).append('=').append(new ItemStack(item).isEnchantable());
		}

		// the branch the mod exists for: an item that already carries an enchantment
		if (!candidates.isEmpty()) {
			ItemStack enchanted = new ItemStack(Items.DIAMOND_SWORD);
			enchanted.enchant(candidates.get(0), 5);
			builder.append("  already_enchanted=").append(enchanted.isEnchantable())
					.append(" (false if reenchanting.allowEquipment is off)");
		}

		return builder.toString();
	}

	/**
 * Reports the enchanting power of one table position, so bookshelves can be verified without a
 * client: four bookcases around a table must read exactly {@code 4 * DEFAULT_POWER}.
 */
	private static int power(CommandSourceStack source, BlockPos pos) {
		EnchantingBlockConfiguration configuration = Configuration.instance.enchantment.enchantingBlocks;
		float total = EnchantingBlocks.countEnchantingPower(source.getLevel(), pos);

		source.sendSuccess(() -> Component.literal("enchanting power at %s = %s (ceiling %s, radius xz[%d..%d] y[%d..%d])"
				.formatted(
						pos.toShortString(),
						total,
						EnchantingBlocks.maxPower(),
						configuration.radius.xz.min,
						configuration.radius.xz.max,
						configuration.radius.y.min,
						configuration.radius.y.max)), false);

		Map<Block, Integer> contributors = new LinkedHashMap<>();

		EnchantingBlocks.forEachInRange(source.getLevel(), pos, (block, offset) -> {
			float power = EnchantingBlocks.enchantingPower(block);

			if (power > 0.0F) {
				contributors.merge(block, 1, Integer::sum);
			}
		});

		contributors.forEach((block, count) -> source.sendSuccess(() ->
				Component.literal("  %dx %s -> %s".formatted(count, block, EnchantingBlocks.enchantingPower(block) * count)), false));

		return com.mojang.brigadier.Command.SINGLE_SUCCESS;
	}

	private static String limits(Registry<Enchantment> registry) {
		return "globalMaxLevel=" + Configuration.instance.enchantment.globalMaxLevel
				+ ", allowTreasure=" + Configuration.instance.enchantment.allowTreasure;
	}

	private static String sample(List<EnchantmentInstance> entries) {
		if (entries.isEmpty()) {
			return "";
		}

		StringBuilder builder = new StringBuilder("[");

		for (int i = 0; i < Math.min(entries.size(), 4); i++) {
			if (i > 0) {
				builder.append(", ");
			}

			Holder<Enchantment> enchantment = entries.get(i).enchantment();
			builder.append(enchantment.unwrapKey().map(key -> key.identifier().toString()).orElse("?"))
					.append('=')
					.append(entries.get(i).level());
		}

		return builder.append(']').toString();
	}
}