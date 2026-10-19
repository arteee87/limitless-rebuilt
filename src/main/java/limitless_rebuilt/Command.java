package limitless_rebuilt;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.ArrayList;
import limitless_rebuilt.enchantment.EnchantingBlocks;
import limitless_rebuilt.config.enchantment.EnchantingBlockConfiguration;
import net.minecraft.util.math.BlockPos;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.block.Block;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import limitless_rebuilt.config.Configuration;
import limitless_rebuilt.config.enchantment.entry.EnchantmentTooltipConfiguration;
import limitless_rebuilt.enchantment.EnchantmentLimits;
import limitless_rebuilt.enchantment.EnchantmentStats;
import limitless_rebuilt.enchantment.EnchantmentUtil;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.DefaultPermissions;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

public enum Command implements CommandRegistrationCallback {
	INSTANCE;

	private static final RegistryKey<Registry<Enchantment>> ENCHANTMENTS = RegistryKeys.ENCHANTMENT;
	private static final int[] POWERS = {1, 5, 15, 30, 60};

	@Override
	public void register(
			CommandDispatcher<ServerCommandSource> dispatcher,
			CommandRegistryAccess registryAccess,
			CommandManager.RegistrationEnvironment environment) {
		// 1.21.11 replaced ServerCommandSource.hasPermissionLevel(int) with a permission predicate.
		// DefaultPermissions.GAMEMASTERS is the constant for the old operator level 2: PermissionLevel
		// numbers its enum ALL..OWNERS as 0..4, which is exactly the scale hasPermissionLevel used.
		dispatcher.register(CommandManager.literal("limitless")
				.requires(source -> source.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS))
				.then(CommandManager.literal("reload")
						.executes(context -> {
							Configuration.reload();

							if (context.getSource().getEntity() instanceof PlayerEntity player) {
								player.sendMessage(Text.literal("Done."), true);
							}

							return com.mojang.brigadier.Command.SINGLE_SUCCESS;
						}))
				.then(CommandManager.literal("debug")
						.executes(context -> diagnose(context.getSource()))
						.then(CommandManager.literal("stats")
								.executes(context -> stats(context.getSource()))))
				.then(CommandManager.literal("power")
						.then(CommandManager.argument("position", BlockPosArgumentType.blockPos())
								.executes(context -> power(context.getSource(),
										BlockPosArgumentType.getLoadedBlockPos(context, "position"))))));
	}

	/**
	 * Prints what the enchanting table would offer at several power levels.
	 *
	 * <p>That selection runs entirely on the client, so it cannot be observed from a log of a normal
	 * session; running the same calls here is the only way to tell an empty candidate list from a
	 * power that matches nothing.
	 */
	private static int diagnose(ServerCommandSource source) {
		ServerWorld world = source.getWorld();
		Registry<Enchantment> registry = world.getRegistryManager().getOrThrow(ENCHANTMENTS);

		source.sendFeedback(() -> Text.literal("limitless debug: " + limits(registry)), false);
		source.sendFeedback(() -> Text.literal("registered enchantments: " + registry.getIds().size()
				+ ", limits applied: " + EnchantmentLimits.size()), false);

		List<RegistryEntry<Enchantment>> candidates = new ArrayList<>();
		registry.iterateEntries(EnchantmentTags.IN_ENCHANTING_TABLE).forEach(candidates::add);
		source.sendFeedback(() -> Text.literal("#minecraft:in_enchanting_table -> "
				+ candidates.size() + " entries"), false);

		source.sendFeedback(() -> Text.literal("isEnchantable: " + enchantability(candidates)), false);

		// goes through the mixin exactly like the item tooltip does; on a server the name part
		// resolves to its key, so only the numeral is meaningful here
		registry.getEntrySet().stream()
				.filter(entry -> entry.getKey().getValue().toString().endsWith("sharpness"))
				.findFirst()
				.ifPresent(entry -> source.sendFeedback(() -> Text.literal(
						"level names: 1='%s' 14='%s' 4000='%s'"
								.formatted(
										Enchantment.getName(registry.getEntry(entry.getValue()), 1).getString(),
										Enchantment.getName(registry.getEntry(entry.getValue()), 14).getString(),
										Enchantment.getName(registry.getEntry(entry.getValue()), 4000).getString())), false));

		for (ItemStack stack : List.of(new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.BOOK))) {
			source.sendFeedback(() -> Text.literal("--- " + stack.getItem().getName().getString()
					+ ", enchantable=" + stack.contains(net.minecraft.component.DataComponentTypes.ENCHANTABLE)
					+ ", isEnchantable=" + stack.isEnchantable()
					+ ", modAccepts=" + EnchantmentUtil.isEnchantable(stack)), false);

			for (int power : POWERS) {
				List<EnchantmentLevelEntry> entries = EnchantmentHelper.getPossibleEntries(
						power, stack, candidates.stream());
				source.sendFeedback(() -> Text.literal("  power " + power + " -> "
						+ entries.size() + " entries " + sample(entries)), false);
			}

			for (int power : new int[]{5, 20}) {
				List<EnchantmentLevelEntry> generated = EnchantmentHelper.generateEnchantments(
						Random.create(power), stack, power, candidates.stream());
				source.sendFeedback(() -> Text.literal("  generateEnchantments(power=" + power + ") -> "
						+ generated.size() + " " + sample(generated)), false);
			}
		}

		registry.getEntrySet().stream()
				.filter(entry -> entry.getKey().getValue().toString().endsWith("sharpness"))
				.findFirst()
				.ifPresent(entry -> {
					Enchantment enchantment = entry.getValue();
					StringBuilder curve = new StringBuilder("  sharpness: minLevel=" + enchantment.getMinLevel()
							+ " maxLevel=" + enchantment.getMaxLevel() + " curve ");

					for (int level = enchantment.getMinLevel(); level <= enchantment.getMinLevel() + 5; level++) {
						curve.append(level).append("->").append(enchantment.getMinPower(level)).append(' ');
					}

					source.sendFeedback(() -> Text.literal(curve.toString()), false);
				});

		source.sendFeedback(() -> Text.literal("anvil levelLimit=" + Configuration.instance.anvil.levelLimit
					+ ", costLimitEnabled=" + Configuration.instance.anvil.costLimitEnabled
					+ ", costLimit=" + Configuration.instance.anvil.costLimit
				+ ", enchantingPower block default=" + net.minecraft.text.Text.literal("see config")
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
	private static int stats(ServerCommandSource source) {
		ServerWorld world = source.getWorld();
		Registry<Enchantment> registry = world.getRegistryManager().getOrThrow(ENCHANTMENTS);
		EnchantmentTooltipConfiguration tooltip = Configuration.instance.enchantment.tooltip;

		source.sendFeedback(() -> Text.literal("enabled=" + tooltip.enabled
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
			RegistryEntry<Enchantment> enchantment = entry(registry, path);

			if (enchantment == null) {
				source.sendFeedback(() -> Text.literal("  missing: " + path), false);
				continue;
			}

			sword.addEnchantment(enchantment, level);
			source.sendFeedback(() -> Text.literal("  " + path + " " + level), false);
		}

		source.sendFeedback(() -> Text.literal("sharpnessBonus=" + EnchantmentStats.sharpnessBonus(sword)), false);

		List<EnchantmentStats.Stat> collected = EnchantmentStats.collect(sword);

		if (collected.isEmpty()) {
			source.sendFeedback(() -> Text.literal("no lines collected"), false);
		}

		for (EnchantmentStats.Stat stat : collected) {
			source.sendFeedback(() -> Text.literal("  " + stat.key()
					+ " style=" + stat.style()
					+ " value=" + EnchantmentStats.render(stat)), false);
		}

		return com.mojang.brigadier.Command.SINGLE_SUCCESS;
	}

	private static RegistryEntry<Enchantment> entry(Registry<Enchantment> registry, String path) {
		return registry.getEntry(Identifier.ofVanilla(path)).orElse(null);
	}

	/**
	 * Whether the table would accept these items at all.
	 *
	 * <p>{@code EnchantmentScreenHandler#onContentChanged} empties all three lapis slots and skips the
	 * whole scan when {@code ItemStack#isEnchantable()} answers {@code false}, which leaves an empty
	 * table with no other symptom; a wrong answer here is therefore the difference between "the table
	 * is broken" and "the table is fine".
	 */
	private static String enchantability(List<RegistryEntry<Enchantment>> candidates) {
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
			enchanted.addEnchantment(candidates.get(0), 5);
			builder.append("  already_enchanted=").append(enchanted.isEnchantable())
					.append(" (false if reenchanting.allowEquipment is off)");
		}

		return builder.toString();
	}

	/**
 * Reports the enchanting power of one table position, so bookshelves can be verified without a
 * client: four bookcases around a table must read exactly {@code 4 * DEFAULT_POWER}.
 */
	private static int power(ServerCommandSource source, BlockPos pos) {
		EnchantingBlockConfiguration configuration = Configuration.instance.enchantment.enchantingBlocks;
		float total = EnchantingBlocks.countEnchantingPower(source.getWorld(), pos);

		source.sendFeedback(() -> Text.literal("enchanting power at %s = %s (ceiling %s, radius xz[%d..%d] y[%d..%d])"
				.formatted(
						pos.toShortString(),
						total,
						EnchantingBlocks.maxPower(),
						configuration.radius.xz.min,
						configuration.radius.xz.max,
						configuration.radius.y.min,
						configuration.radius.y.max)), false);

		Map<Block, Integer> contributors = new LinkedHashMap<>();

		EnchantingBlocks.forEachInRange(source.getWorld(), pos, (block, offset) -> {
			float power = EnchantingBlocks.enchantingPower(block);

			if (power > 0.0F) {
				contributors.merge(block, 1, Integer::sum);
			}
		});

		contributors.forEach((block, count) -> source.sendFeedback(() ->
				Text.literal("  %dx %s -> %s".formatted(count, block, EnchantingBlocks.enchantingPower(block) * count)), false));

		return com.mojang.brigadier.Command.SINGLE_SUCCESS;
	}

	private static String limits(Registry<Enchantment> registry) {
		return "globalMaxLevel=" + Configuration.instance.enchantment.globalMaxLevel
				+ ", allowTreasure=" + Configuration.instance.enchantment.allowTreasure;
	}

	private static String sample(List<EnchantmentLevelEntry> entries) {
		if (entries.isEmpty()) {
			return "";
		}

		StringBuilder builder = new StringBuilder("[");

		for (int i = 0; i < Math.min(entries.size(), 4); i++) {
			if (i > 0) {
				builder.append(", ");
			}

			RegistryEntry<Enchantment> enchantment = entries.get(i).enchantment();
			builder.append(enchantment.getKey().map(key -> key.getValue().toString()).orElse("?"))
					.append('=')
					.append(entries.get(i).level());
		}

		return builder.append(']').toString();
	}
}