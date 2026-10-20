package limitless_rebuilt.mixin;

import java.util.List;
import java.util.stream.Stream;
import limitless_rebuilt.LimitlessLogger;
import limitless_rebuilt.config.Configuration;
import limitless_rebuilt.enchantment.EnchantingBlocks;
import limitless_rebuilt.enchantment.EnchantmentLimits;
import limitless_rebuilt.enchantment.EnchantmentUtil;
import limitless_rebuilt.enchantment.ExperienceUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enchanting table behaviour.
 *
 * <p>1.19.3 rewrote the bytecode of {@code generateEnchantments} to swap the fifteen bookshelf
 * positions for {@code EnchantingBlocks.countEnchantingPower} and to call the mod's own level
 * formula. Here the same three things are done with injections on the surrounding calls:
 * the bookshelf scan, the level formula, and the candidate list.
 */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentScreenHandlerMixin {
	/**
	 * {@code onContentChanged} and {@code onButtonClick} only hand a lambda to the screen handler
	 * context, so the code that has to be patched lives in the synthetic method the compiler moved it
	 * into. Yarn gives those two methods no name, which means the name below is identical in the
	 * named and in the intermediary namespace and needs no remapping.
	 */
	private static final String CONTENT_CHANGED_BODY = "lambda$slotsChanged$0";
	private static final String BUTTON_CLICK_BODY = "lambda$clickMenuButton$0";

	@Shadow
	public int[] costs;

	@Unique
	private float limitlessPower = -1.0F;

	@Unique
	private Registry<Enchantment> limitlessRegistry;

	/**
	 * The last line each debug site printed, so an unchanged one is not repeated.
	 *
	 * <p>{@code onContentChanged} runs several times per tick while the screen is open, and
	 * {@code generateEnchantments} once per slot on top of that, so an unguarded line repeats five or six
	 * times for a single opening and buries everything around it. The anvil guards its lines this way.
	 */
	@Unique
	private String limitlessLastOpened;

	@Unique
	private String limitlessLastPower;

	/** Indexed by slot, because the three messages differ only in the slot number. */
	@Unique
	private String[] limitlessLastGenerated = new String[3];

	@Inject(method = "slotsChanged", at = @At("HEAD"))
	private void limitless_resetPower(CallbackInfo info) {
		this.limitlessPower = -1.0F;

		if (Configuration.instance.debug) {
			String message = "table opened, item in slot 0 = %s"
					.formatted(describeTarget(((EnchantmentMenu) (Object) this).getSlot(0).getItem()));

			if (!message.equals(this.limitlessLastOpened)) {
				this.limitlessLastOpened = message;
				LimitlessLogger.debug(message);
			}
		}
	}

	private static String describeTarget(ItemStack stack) {
		return stack.isEmpty() ? "empty" : stack.getItem().toString();
	}

	/**
	 * The vanilla scan visits fifteen fixed offsets and counts a bookcase as one. Counting is kept
	 * for the control flow, but the value is taken from the configured radius the first time around.
	 */
	@Redirect(
			method = CONTENT_CHANGED_BODY,
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/block/EnchantingTableBlock;isValidBookShelf(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Z"
			)
	)
	private boolean limitless_powerProvider(Level world, BlockPos table, BlockPos offset) {
		if (this.limitlessPower < 0.0F) {
			this.limitlessPower = EnchantingBlocks.countEnchantingPower(world, table);

			String message = "enchanting power at %s = %s (ceiling %s, radius xz[%d..%d] y[%d..%d])"
					.formatted(
							table.toShortString(),
							this.limitlessPower,
							EnchantingBlocks.maxPower(),
							Configuration.instance.enchantment.enchantingBlocks.radius.xz.min,
							Configuration.instance.enchantment.enchantingBlocks.radius.xz.max,
							Configuration.instance.enchantment.enchantingBlocks.radius.y.min,
							Configuration.instance.enchantment.enchantingBlocks.radius.y.max);

			if (!message.equals(this.limitlessLastPower)) {
				this.limitlessLastPower = message;
				LimitlessLogger.debug(message);
			}
		}

		// vanilla already resolved the offset against the table before calling, so this is an
		// absolute position and must not be added to the table a second time
		return EnchantingBlocks.enchantingPower(world.getBlockState(offset).getBlock()) > 0.0F;
	}

	/**
	 * Bounds the number of experience levels the table demands.
	 *
* <p>Vanilla checks {@code player.experienceLevel < enchantmentPower[id]} next to the untouched
 * {@code experienceLevel < id + 1}, and {@code enchantmentPower} is the level the table offers,
 * which the mod raises together with the bookshelf power. Without a bound a table with a few
 * hundred bookcases would demand a thousand levels for the price of three.
 *
 * <p>The comparison itself is an {@code if_icmpge} with nothing to hook, and Mixin rejects an array
 * load as an injection point, so the {@code enchantmentPower} field read that feeds it is redirected
 * instead. The field is read three times in this method — the length check, the {@code <= 0} check,
 * and this one — and the two others come first, so {@code ordinal = 2} is this one. The array it
 * returns is consumed by the very next instruction and dropped, so clamping it cannot reach the
 * generator, which reads the original from {@link #CONTENT_CHANGED_BODY}.
 */
	@Redirect(
			method = "clickMenuButton",
			at = @At(
					value = "FIELD",
					target = "Lnet/minecraft/world/inventory/EnchantmentMenu;costs:[I",
					ordinal = 2
			)
	)
	private int[] limitless_levelRequirement(EnchantmentMenu handler) {
		return ExperienceUtil.capRequiredLevels(
				handler.costs,
				Configuration.instance.enchantment.maxLevelRequirement);
	}

	/**
	 * Reports what a slot demands, which is the only way to tell a capped requirement from one that
	 * silently still asks for hundreds of levels.
	 */
	@Inject(method = "clickMenuButton", at = @At("HEAD"))
	private void limitless_logRequirement(net.minecraft.world.entity.player.Player player, int slot,
			CallbackInfoReturnable<Boolean> info) {
		if (!Configuration.instance.debug || slot < 0 || slot >= this.costs.length) {
			return;
		}

		int offered = this.costs[slot];
		int required = ExperienceUtil.capRequiredLevel(
				offered,
				Configuration.instance.enchantment.maxLevelRequirement);

		LimitlessLogger.debug("click on slot %d: offered level=%d, required=%d (limit %d), player has %d -> %s"
				.formatted(
						slot,
						offered,
						required,
						Configuration.instance.enchantment.maxLevelRequirement,
						player.experienceLevel,
						player.experienceLevel >= required ? "affordable" : "NOT affordable"));
	}

	/** Logs what a slot actually offers, which is where an empty list has to be traced back from. */
	@Inject(method = "getEnchantmentList", at = @At("RETURN"))
	private void limitless_logGenerated(
			RegistryAccess registryManager,
			ItemStack stack,
			int slot,
			int level,
			CallbackInfoReturnable<List<EnchantmentInstance>> info) {
		String message = "slot %d: level=%d stack=%s candidates=%d results=%d%s limits=%d"
				.formatted(
						slot,
						level,
						stack.isEmpty() ? "empty" : stack.getItem(),
						EnchantmentUtil.candidateCount(registryManager),
						info.getReturnValue().size(),
						EnchantmentUtil.describe(info.getReturnValue()),
						EnchantmentLimits.size());

		if (slot >= 0 && slot < this.limitlessLastGenerated.length
				&& !message.equals(this.limitlessLastGenerated[slot])) {
			this.limitlessLastGenerated[slot] = message;
			LimitlessLogger.debug(message);
		}
	}


	/**
	 * Replaces the vanilla formula, which treats its argument as a bookshelf count capped at fifteen,
	 * with the one of the mod, which uses the accumulated power directly.
	 */
	@Redirect(
			method = CONTENT_CHANGED_BODY,
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getEnchantmentCost(Lnet/minecraft/util/RandomSource;IILnet/minecraft/world/item/ItemStack;)I"
			)
	)
	private int limitless_requiredLevel(RandomSource random, int slotIndex, int bookshelfCount, ItemStack stack) {
		float power = this.limitlessPower < 0.0F ? 0.0F : this.limitlessPower;
		return EnchantingBlocks.calculateRequiredExperienceLevel(random, slotIndex, power, stack);
	}

	@Redirect(
			method = "getEnchantmentList",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/core/RegistryAccess;lookupOrThrow(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Registry;"
			)
	)
	private Registry<Enchantment> limitless_captureRegistry(
			RegistryAccess manager,
			ResourceKey<Registry<Enchantment>> key) {
		Registry<Enchantment> registry = manager.lookupOrThrow(key);
		this.limitlessRegistry = registry;
		return registry;
	}

	/**
	 * Vanilla draws its candidates from {@code #minecraft:in_enchanting_table}, which is
	 * {@code #minecraft:non_treasure}; the mod adds {@code #minecraft:treasure} back when the
	 * configuration allows it.
	 */
	@Redirect(
			method = "getEnchantmentList",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/core/HolderSet$Named;stream()Ljava/util/stream/Stream;")
	)
	private Stream<Holder<Enchantment>> limitless_allowTreasure(HolderSet.Named<Enchantment> candidates) {
		Registry<Enchantment> registry = this.limitlessRegistry;

		if (registry == null || !Configuration.instance.enchantment.allowTreasure) {
			return candidates.stream();
		}

		Stream<Holder<Enchantment>> treasure = registry.entrySet().stream()
				.map(candidate -> registry.wrapAsHolder(candidate.getValue()))
				.filter(candidate -> candidate.is(EnchantmentTags.TREASURE));

		return Stream.concat(candidates.stream(), treasure);
	}

	/**
	 * Vanilla replaces the enchantment the lap produced with {@code ItemStack#addEnchantment}, which
	 * overwrites an existing one; the mod merges instead when configured to.
	 */
	@Redirect(
			method = BUTTON_CLICK_BODY,
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/ItemStack;enchant(Lnet/minecraft/core/Holder;I)V"
			),
			require = 0
	)
	private void limitless_mergeEnchantment(
			ItemStack stack,
			Holder<Enchantment> enchantment,
			int level) {
		if (!EnchantmentUtil.mergeEnchantment(
				stack, enchantment, level, Configuration.instance.enchantment.conflicts.merge)) {
			stack.enchant(enchantment, level);
		}
	}
}
