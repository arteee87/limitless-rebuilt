package limitless_rebuilt.mixin;

import limitless_rebuilt.LimitlessLogger;
import limitless_rebuilt.config.Configuration;
import limitless_rebuilt.config.anvil.AnvilConfiguration;
import limitless_rebuilt.enchantment.ExperienceUtil;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import java.util.HashSet;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.Unique;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Anvil behaviour.
 *
 * <p>1.19.3 patched this class the same way, across two mixin classes: one for the anvil rules and
 * one for cost normalization. They are merged here, and the level limit now targets the literal it
 * originally targeted rather than the cost property.
 *
 * <p>Priority is {@code -1000} so that this applies after other mods have rewritten the same
 * injection points; the 1.19.3 mixin carried it too.
 */
@Mixin(value = AnvilMenu.class, priority = -1000)
public abstract class AnvilScreenHandlerMixin {
	private static final int VANILLA_MINIMUM_COST = 40;

	/**
	 * The last message printed by each of the three log points below.
	 *
	 * <p>{@code canTakeOutput} is reached once per drawn frame through
	 * {@code AnvilScreen#drawForeground} calling {@code Slot#canTakeItems}, and {@code updateResult}
	 * runs several times per slot update, so logging unconditionally floods the file at frame rate. The
	 * text is built from the whole state, so printing only on a change keeps every line informative:
	 * anything that moved prints, and a stretch where nothing moved prints once. It matches how the
	 * enchanting table already gates its {@code render} debug behind a printed-once flag, except that
	 * an anvil's state really does change while the screen stays open, so the test has to be "changed"
	 * rather than "first frame".
	 *
	 * <p>The client and the integrated server each own a handler, so each keeps its own set and both
	 * print their own transitions.
	 */
	@Unique
	private String limitlessLastInput;

	@Unique
	private String limitlessLastResult;

	@Unique
	private String limitlessLastCanTakeOutput;

	/**
	 * Vanilla doubles the repair cost every time the anvil is used; the mod keeps it constant, which
	 * covers both "constant repair cost" and "anvil cost does not grow".
	 */
	@Inject(method = "calculateIncreasedRepairCost", at = @At("HEAD"), cancellable = true)
	private static void limitless_getNextCost(int cost, CallbackInfoReturnable<Integer> info) {
		// The repair cost never grows: vanilla's getNextCost doubles it on every use, which is what
		// makes the anvil cost spiral. Replacing it with zero keeps the cost constant.
		info.setReturnValue(0);
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
			method = "createResult",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/enchantment/Enchantment;canEnchant(Lnet/minecraft/world/item/ItemStack;)Z"
			)
	)
	private boolean allowIncompatibleItem(boolean acceptable) {
		boolean allowed = acceptable || Configuration.instance.anvil.mergeIncompatible;

		if (!acceptable && allowed) {
			LimitlessLogger.debug("anvil item: NOT acceptable by vanilla -> allowed by mergeIncompatible");
		}

		return allowed;
	}

	/**
	 * The conflicts already reported for the current combination in the two slots.
	 *
	 * <p>{@code canBeCombined} is asked once per pair of enchantments, and every recomputation asks
	 * again, so an unguarded line repeats for each of them — nine lines for one item with three
	 * conflicts, and {@code updateResult} alone runs several times per action. Clearing it per
	 * recomputation therefore barely helped; the unit that matches what a reader wants is the
	 * <em>combination</em>, so the set is cleared where the anvil input is compared against the last one.
	 *
	 * <p>The set is static because the redirect handler is: it wraps a static call, so Mixin requires a
	 * static handler and there is no instance to hang state off. Both sides own a handler and both will
	 * therefore report into it, and a pair seen by one side is not repeated by the other — which is the
	 * point. The input, result, display and {@code canTakeOutput} lines still come out on both sides.
	 */
	@Unique
	private static Set<String> limitlessConflictSeen = new HashSet<>();

	/** Allows applying an enchantment that conflicts with one already on the item. */
	@Redirect(
			method = "createResult",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/enchantment/Enchantment;areCompatible(Lnet/minecraft/core/Holder;Lnet/minecraft/core/Holder;)Z"
			),
			require = 0
	)
	private static boolean allowConflictingEnchantment(Holder<Enchantment> first, Holder<Enchantment> second) {
		boolean combined = Enchantment.areCompatible(first, second);
		boolean allowed = combined || Configuration.instance.anvil.mergeConflicts;

		if (!combined && allowed) {
			String message = "anvil enchantments: %s and %s conflict -> allowed by mergeConflicts"
					.formatted(name(first), name(second));

			if (limitlessConflictSeen.add(message)) {
				LimitlessLogger.debug(message);
			}
		}

		return allowed;
	}

	private static String name(Holder<Enchantment> entry) {
		return entry.unwrapKey().map(key -> key.identifier().toString()).orElse("unknown");
	}

	/**
	 * Vanilla refuses to produce a result once the cost reaches forty levels; {@code levelLimit}
	 * replaces that threshold. 1.19.3 did the same thing with a single {@code @ModifyConstant}.
	 *
	 * <p><b>An earlier port clamped the cost property instead, and that cannot work.</b> Reading the
	 * cost through {@code Math.min(cost, levelLimit)} only ever lowers the value the comparisons see:
	 * with {@code levelLimit} at its {@link Integer#MAX_VALUE} default the cost arrives unchanged, the
	 * threshold stays forty, and an expensive combination is still emptied at the line below. The
	 * limit has to replace the literal, not the number it is compared against.
	 *
	 * <p>Three constants of forty live in {@code updateResult}, and only two of them are the
	 * threshold. The first is the unrelated "a stack of more than one item costs forty" rule
	 * (bytecode offset 723), which the 1.19.3 mod also rewrote and thereby made the anvil refuse any
	 * stack; {@link #modifyRenameThreshold} and {@link #modifyResultThreshold} take the two
	 * comparisons separately and leave that one alone.
	 *
	 * <p>Mixin 0.8.7 puts the {@code ordinal} on {@link Constant} rather than on
	 * {@code @ModifyConstant} itself. Numbering only the constants that are forty, in bytecode order,
	 * they are 0 = the stack rule, 1 = this rename-only cap and 2 = the gate below.
	 */
	@ModifyConstant(method = "createResult", constant = @Constant(intValue = VANILLA_MINIMUM_COST, ordinal = 1))
	private int modifyRenameThreshold(int forty) {
		return Configuration.instance.anvil.effectiveLevelLimit();
	}

	/**
	 * The gate that empties the result outside creative mode; the one that matters. This is the
	 * single line that made a priced enchantment book come back as a red cross.
	 */
	@ModifyConstant(method = "createResult", constant = @Constant(intValue = VANILLA_MINIMUM_COST, ordinal = 2))
	private int modifyResultThreshold(int forty) {
		return Configuration.instance.anvil.effectiveLevelLimit();
	}

	/**
	 * Caps the calculated cost when the option is on. The clamp at offset 862 is the single place the
	 * final cost is computed (a long, clamped to the int range), so capping its result covers repair,
	 * combination and rename in one place and the value the gate, canTakeOutput, onTakeOutput and the
	 * client display all see is the capped one.
	 */
	@ModifyExpressionValue(
			method = "createResult",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/util/Mth;clamp(JJJ)J"
			)
	)
	private long limitCost(long cost) {
		AnvilConfiguration anvil = Configuration.instance.anvil;
		return anvil.costLimitEnabled ? Math.min(cost, anvil.costLimit) : cost;
	}

	/**
	 * Replaces the deduction when the player sits well above the cost.
	 *
	 * <p>Vanilla drops the player to the resulting level, so a high level payer loses almost
	 * everything; subtracting the equivalent experience of a level zero player keeps their levels.
	 * 1.19.3 kept this in its own mixin class; it only ever touches the deduction, so whether the
	 * player may take the result at all stays vanilla.
	 */
	@Redirect(
			method = "onTake",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/Player;giveExperienceLevels(I)V"
			)
	)
	private void normalizeCost(Player player, int levels) {
		if (Configuration.instance.anvil.normalization.enabled && player.experienceLevel > levels) {
			ExperienceUtil.addExperienceLevelsNormalized(player, levels);
		} else {
			player.giveExperienceLevels(levels);
		}
	}

	/** Records the inputs before they are combined, so a rejected input can be traced back. */
	@Inject(method = "createResult", at = @At("HEAD"))
	private void logInput(CallbackInfo info) {
		if (!Configuration.instance.debug) {
			return;
		}

		AnvilMenu handler = (AnvilMenu) (Object) this;

		String message = LimitlessLogger.side()
				+ " anvil input: slot0=%s slot1=%s mergeConflicts=%s mergeIncompatible=%s levelLimit=%d costLimitEnabled=%s costLimit=%d"
						.formatted(describe(handler.getSlot(0).getItem()),
								describe(handler.getSlot(1).getItem()),
								Configuration.instance.anvil.mergeConflicts,
								Configuration.instance.anvil.mergeIncompatible,
								Configuration.instance.anvil.levelLimit,
								Configuration.instance.anvil.costLimitEnabled,
								Configuration.instance.anvil.costLimit);

		if (!message.equals(this.limitlessLastInput)) {
			this.limitlessLastInput = message;
			limitlessConflictSeen.clear();
			LimitlessLogger.debug(message);
		}
	}

	/**
	 * Reports the outcome, which is the line that tells "the anvil is broken" apart from "the anvil
	 * worked and the combination was simply too expensive for this player".
	 */
	@Inject(method = "createResult", at = @At("RETURN"))
	private void logResult(CallbackInfo info) {
		if (!Configuration.instance.debug) {
			return;
		}

		AnvilMenu handler = (AnvilMenu) (Object) this;

		String message = LimitlessLogger.side() + " anvil result: cost=%d output=%s"
				.formatted(handler.getCost(),
						describe(handler.getSlot(2).getItem()));

		if (!message.equals(this.limitlessLastResult)) {
			this.limitlessLastResult = message;
			LimitlessLogger.debug(message);
		}
	}

	/**
	 * Logs the affordability gate. Vanilla keeps this check untouched, so a player below the cost
	 * cannot take the result no matter what {@code levelLimit} says; normalization only changes what
	 * is deducted afterwards, and only when the player is above the cost.
	 */
	@Inject(method = "mayPickup", at = @At("RETURN"))
	private void logCanTakeOutput(Player player, boolean present, CallbackInfoReturnable<Boolean> info) {
		if (!Configuration.instance.debug) {
			return;
		}

		String message = LimitlessLogger.side()
				+ " anvil canTakeOutput: level=%d cost=%d normalized=%s -> %s"
						.formatted(player.experienceLevel,
								((AnvilMenu) (Object) this).getCost(),
								Configuration.instance.anvil.normalization.enabled,
								info.getReturnValue());

		if (!message.equals(this.limitlessLastCanTakeOutput)) {
			this.limitlessLastCanTakeOutput = message;
			LimitlessLogger.debug(message);
		}
	}

	private static String describe(ItemStack stack) {
		if (stack.isEmpty()) {
			return "empty";
		}

		return stack.getItem() + (stack.getCount() > 1 ? " x" + stack.getCount() : "");
	}
}
