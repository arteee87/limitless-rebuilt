package limitless_rebuilt.mixin;

import java.util.List;
import java.util.stream.Stream;
import limitless_rebuilt.LimitlessLogger;
import limitless_rebuilt.config.Configuration;
import limitless_rebuilt.enchantment.EnchantingBlocks;
import limitless_rebuilt.enchantment.EnchantmentLimits;
import limitless_rebuilt.enchantment.EnchantmentUtil;
import limitless_rebuilt.enchantment.ExperienceUtil;
import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
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
@Mixin(EnchantmentScreenHandler.class)
public abstract class EnchantmentScreenHandlerMixin {
	/**
	 * {@code onContentChanged} and {@code onButtonClick} only hand a lambda to the screen handler
	 * context, so the code that has to be patched lives in the synthetic method the compiler moved it
	 * into. Yarn gives those two methods no name, which means the name below is identical in the
	 * named and in the intermediary namespace and needs no remapping.
	 */
	private static final String CONTENT_CHANGED_BODY = "method_17411";
	private static final String BUTTON_CLICK_BODY = "method_17410";

	@Shadow
	public int[] enchantmentPower;

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

	@Inject(method = "onContentChanged", at = @At("HEAD"))
	private void limitless_resetPower(CallbackInfo info) {
		this.limitlessPower = -1.0F;

		if (Configuration.instance.debug) {
			String message = "table opened, item in slot 0 = %s"
					.formatted(describeTarget(((EnchantmentScreenHandler) (Object) this).getSlot(0).getStack()));

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
					target = "Lnet/minecraft/block/EnchantingTableBlock;canAccessPowerProvider"
							+ "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/BlockPos;)Z"
			)
	)
	private boolean limitless_powerProvider(World world, BlockPos table, BlockPos offset) {
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
			method = "onButtonClick",
			at = @At(
					value = "FIELD",
					target = "Lnet/minecraft/screen/EnchantmentScreenHandler;enchantmentPower:[I",
					ordinal = 2
			)
	)
	private int[] limitless_levelRequirement(EnchantmentScreenHandler handler) {
		return ExperienceUtil.capRequiredLevels(
				handler.enchantmentPower,
				Configuration.instance.enchantment.maxLevelRequirement);
	}

	/**
	 * Reports what a slot demands, which is the only way to tell a capped requirement from one that
	 * silently still asks for hundreds of levels.
	 */
	@Inject(method = "onButtonClick", at = @At("HEAD"))
	private void limitless_logRequirement(net.minecraft.entity.player.PlayerEntity player, int slot,
			CallbackInfoReturnable<Boolean> info) {
		if (!Configuration.instance.debug || slot < 0 || slot >= this.enchantmentPower.length) {
			return;
		}

		int offered = this.enchantmentPower[slot];
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
	@Inject(method = "generateEnchantments", at = @At("RETURN"))
	private void limitless_logGenerated(
			DynamicRegistryManager registryManager,
			ItemStack stack,
			int slot,
			int level,
			CallbackInfoReturnable<List<EnchantmentLevelEntry>> info) {
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
					target = "Lnet/minecraft/enchantment/EnchantmentHelper;calculateRequiredExperienceLevel"
							+ "(Lnet/minecraft/util/math/random/Random;IILnet/minecraft/item/ItemStack;)I"
			)
	)
	private int limitless_requiredLevel(Random random, int slotIndex, int bookshelfCount, ItemStack stack) {
		float power = this.limitlessPower < 0.0F ? 0.0F : this.limitlessPower;
		return EnchantingBlocks.calculateRequiredExperienceLevel(random, slotIndex, power, stack);
	}

	@Redirect(
			method = "generateEnchantments",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/registry/DynamicRegistryManager;getOrThrow"
							+ "(Lnet/minecraft/registry/RegistryKey;)Lnet/minecraft/registry/Registry;"
			)
	)
	private Registry<Enchantment> limitless_captureRegistry(
			DynamicRegistryManager manager,
			RegistryKey<Registry<Enchantment>> key) {
		Registry<Enchantment> registry = manager.getOrThrow(key);
		this.limitlessRegistry = registry;
		return registry;
	}

	/**
	 * Vanilla draws its candidates from {@code #minecraft:in_enchanting_table}, which is
	 * {@code #minecraft:non_treasure}; the mod adds {@code #minecraft:treasure} back when the
	 * configuration allows it.
	 */
	@Redirect(
			method = "generateEnchantments",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/registry/entry/RegistryEntryList$Named;stream()Ljava/util/stream/Stream;")
	)
	private Stream<RegistryEntry<Enchantment>> limitless_allowTreasure(RegistryEntryList.Named<Enchantment> candidates) {
		Registry<Enchantment> registry = this.limitlessRegistry;

		if (registry == null || !Configuration.instance.enchantment.allowTreasure) {
			return candidates.stream();
		}

		Stream<RegistryEntry<Enchantment>> treasure = registry.getEntrySet().stream()
				.map(candidate -> registry.getEntry(candidate.getValue()))
				.filter(candidate -> candidate.isIn(EnchantmentTags.TREASURE));

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
					target = "Lnet/minecraft/item/ItemStack;addEnchantment"
							+ "(Lnet/minecraft/registry/entry/RegistryEntry;I)V"
			),
			require = 0
	)
	private void limitless_mergeEnchantment(
			ItemStack stack,
			RegistryEntry<Enchantment> enchantment,
			int level) {
		if (!EnchantmentUtil.mergeEnchantment(
				stack, enchantment, level, Configuration.instance.enchantment.conflicts.merge)) {
			stack.addEnchantment(enchantment, level);
		}
	}
}
