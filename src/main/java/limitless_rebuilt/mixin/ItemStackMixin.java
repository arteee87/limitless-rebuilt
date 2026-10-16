package limitless_rebuilt.mixin;

import limitless_rebuilt.AttributeTooltipContext;
import limitless_rebuilt.LimitlessLogger;
import limitless_rebuilt.config.Configuration;
import limitless_rebuilt.config.enchantment.entry.ReenchantingConfiguration;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.ItemStack;
import java.util.List;
import java.util.function.Consumer;
import limitless_rebuilt.config.enchantment.entry.EnchantmentTooltipConfiguration;
import limitless_rebuilt.enchantment.EnchantmentStats;
import limitless_rebuilt.enchantment.EnchantmentUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Re-enchanting.
 *
 * <p>Vanilla refuses to enchant a stack that already carries enchantments, which makes an enchanting
 * table a one shot tool. 1.19.3 redirected {@code ItemStack#hasEnchantments()} inside
 * {@code isEnchantable}; the same call is now {@code ItemEnchantmentsComponent#isEmpty()}.
 *
 * <p>The check must stay an {@code or}: re-enchanting being enabled means the stack may be enchanted
 * again whatever it already carries, and only when it is disabled does vanilla's "no enchantments
 * yet" rule apply. Written as an {@code and} against the negated flag the method answers {@code false}
 * for every item, and {@code EnchantmentScreenHandler#onContentChanged} clears all three lapis slots
 * on that answer, leaving the table permanently empty.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	/**
	 * Every line this mod adds.
	 *
	 * <p>Vanilla paints its attribute lines per attribute — green for attack damage and speed, blue
	 * for armour — so an unstyled line of our own lands in the middle of that block as white and the
	 * tooltip reads as three different colours at once. {@link Formatting#BLUE} is the 0x5555FF
	 * vanilla itself uses for the armour attributes; one colour for all of ours settles it. Do not
	 * "fix" it to {@code GRAY}: that is the colour of an enchantment's own name, and these lines
	 * answer a different question.
	 */
	private static final Formatting STAT_COLOR = Formatting.BLUE;

	@Redirect(
			method = "isEnchantable",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/component/type/ItemEnchantmentsComponent;isEmpty()Z"
			)
	)
	private boolean allowReenchanting(ItemEnchantmentsComponent enchantments) {
		return Configuration.instance.enchantment.reenchanting.allowEquipment() || enchantments.isEmpty();
	}

	@Unique
	private String limitlessLastBookCheck;

	/**
	 * Lets an enchanted book be enchanted again.
	 *
	 * <p>{@code isEnchantable} has two gates: the stack must carry {@code enchantable}, and its
	 * {@code enchantments} component must be empty. A book keeps its enchantments in
	 * {@code stored_enchantments} instead, so it satisfies the second gate but not the first — which is
	 * why an enchanted book is refused by the table in vanilla.
	 *
	 * <p>1.19.3 overrode {@code EnchantedBookItem#getEnchantability()} to fix this. Up to 1.21.4 the mod
	 * added the component instead, from an {@code @Inject} into {@code Item.postProcessComponents},
	 * which 1.21.11 deleted along with every call to it — the stack constructor is now three field
	 * assignments. There is no post-process hook left to hook, so the answer is given where the question
	 * is asked instead.
	 *
	 * <p>Adding the component would need the four places 1.21.4 called the hook from, and would leave a
	 * stack mutated by what reads as a query. Cancelling the method answers the same question with one
	 * anchor and no mutation. Books carry no {@code enchantable} in either version — checked in the
	 * item definition, not assumed — so returning early here matches what the redirect below would have
	 * decided anyway: the book's own {@code enchantments} component is empty.
	 *
	 * <p><b>Diagnosing note.</b> This refuses to enchant an already enchanted book in 1.21.11 and the
	 * log does not say why: the table logs nothing at all for a book, which is what a {@code false} from
	 * this method looks like, but which half of the condition failed is invisible. The line below names
	 * both halves and the verdict. It is deduped because the table asks the question every tick.
	 */
	@Inject(
			method = "isEnchantable",
			at = @At("HEAD"),
			cancellable = true
	)
	private void allowEnchantedBooks(CallbackInfoReturnable<Boolean> info) {
		ItemStack stack = (ItemStack) (Object) this;

		if (!stack.contains(DataComponentTypes.STORED_ENCHANTMENTS)) {
			return;
		}

		ReenchantingConfiguration reenchanting = Configuration.instance.enchantment.reenchanting;
		boolean allowed = reenchanting.allowEnchantedBooks();

		String message = "isEnchantable: stack=%s enabled=%s allowEnchantedBooks=%s hasStored=%s -> %s"
				.formatted(
						stack.getItem(),
						reenchanting.enabled,
						reenchanting.allowEnchantedBooks,
						true,
						allowed ? "cancelling" : "NOT cancelling");

		if (!message.equals(this.limitlessLastBookCheck)) {
			this.limitlessLastBookCheck = message;
			LimitlessLogger.debug(message);
		}

		// the shared predicate decides, so this cannot answer true while the level formula answers zero
		if (EnchantmentUtil.isEnchantable(stack)) {
			info.setReturnValue(true);
		}
	}

	/**
	 * Opens the attribute tooltip pass.
	 *
	 * <p>1.21.11 draws the lines inside {@code AttributeModifiersComponent.Display.Default}, which is
	 * handed the player, the attribute and the modifier but never the stack. {@link AttributeTooltipContext}
	 * carries the stack across that gap, and {@link AttributeDisplayMixin} folds the sharpness bonus
	 * into the attack damage line using it.
	 */
	@Inject(
			method = "appendAttributeModifiersTooltip",
			at = @At("HEAD")
	)
	private void openAttributeTooltip(
			Consumer<Text> lines,
			TooltipDisplayComponent display,
			PlayerEntity player,
			CallbackInfo info) {
		AttributeTooltipContext.begin((ItemStack) (Object) this);
	}

	/** Closes the pass on both returns, the early one included. */
	@Inject(
			method = "appendAttributeModifiersTooltip",
			at = @At("RETURN")
	)
	private void closeAttributeTooltip(
			Consumer<Text> lines,
			TooltipDisplayComponent display,
			PlayerEntity player,
			CallbackInfo info) {
		AttributeTooltipContext.end();
	}

	/**
	 * Adds one line per damage kind once the attribute block is done.
	 *
	 * <p>The method has two returns: an early one when the tooltip display component declines to show
	 * the attribute block at all, and the real one after the slot loop. {@code ordinal = 1} is the
	 * latter, so these lines appear whether or not vanilla printed its own, and the sharpness folded into
	 * the attack damage line is repeated here only when that line never existed.
	 *
	 * <p>1.21.11 inserted a {@link TooltipDisplayComponent} between the consumer and the player.
	 */
	@Inject(
			method = "appendAttributeModifiersTooltip",
			at = @At(value = "RETURN", ordinal = 1)
	)
	private void addEnchantmentStats(
			Consumer<Text> lines,
			TooltipDisplayComponent display,
			PlayerEntity player,
			CallbackInfo info) {
		EnchantmentTooltipConfiguration configuration = Configuration.instance.enchantment.tooltip;

		if (!configuration.enabled) {
			return;
		}

		List<EnchantmentStats.Stat> stats = EnchantmentStats.collect((ItemStack) (Object) this);

		if (!AttributeTooltipContext.sawAttackDamage()) {
			float bonus = (float) EnchantmentStats.sharpnessBonus((ItemStack) (Object) this);

			if (bonus != 0.0F) {
				lines.accept(Text.translatable("tooltip.limitless.attack_damage",
						EnchantmentStats.format(bonus)).formatted(STAT_COLOR));
			}
		}

		for (EnchantmentStats.Stat stat : stats) {
			lines.accept(Text.translatable(stat.key(), EnchantmentStats.render(stat)).formatted(STAT_COLOR));
		}
	}
}