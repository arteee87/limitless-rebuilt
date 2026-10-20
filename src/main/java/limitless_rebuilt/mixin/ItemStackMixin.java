package limitless_rebuilt.mixin;

import limitless_rebuilt.AttributeTooltipContext;
import limitless_rebuilt.LimitlessLogger;
import limitless_rebuilt.config.Configuration;
import limitless_rebuilt.config.enchantment.entry.ReenchantingConfiguration;
import java.util.List;
import java.util.function.Consumer;
import org.apache.commons.lang3.mutable.MutableBoolean;
import limitless_rebuilt.config.enchantment.entry.EnchantmentTooltipConfiguration;
import limitless_rebuilt.enchantment.EnchantmentStats;
import limitless_rebuilt.enchantment.EnchantmentUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.ItemEnchantments;
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
	 * tooltip reads as three different colours at once. {@link ChatFormatting#BLUE} is the 0x5555FF
	 * vanilla itself uses for the armour attributes; one colour for all of ours settles it. Do not
	 * "fix" it to {@code GRAY}: that is the colour of an enchantment's own name, and these lines
	 * answer a different question.
	 */
	private static final ChatFormatting STAT_COLOR = ChatFormatting.BLUE;

	@Redirect(
			method = "isEnchantable",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/enchantment/ItemEnchantments;isEmpty()Z"
			)
	)
	private boolean allowReenchanting(ItemEnchantments enchantments) {
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

		if (!stack.has(DataComponents.STORED_ENCHANTMENTS)) {
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
			method = "addAttributeTooltips",
			at = @At("HEAD")
	)
	private void openAttributeTooltip(
			Consumer<Component> lines,
			TooltipDisplay display,
			Player player,
			CallbackInfo info) {
		AttributeTooltipContext.begin((ItemStack) (Object) this);
	}

	/**
	 * Notes which group vanilla left open.
	 *
	 * <p>Vanilla prints {@code item.modifiers.<slot>} once per group and then every line under it, all
	 * through this helper. Which slot it is walking arrives as an argument here, but hooking the method
	 * itself is not enough: {@code ItemStack#appendAttributeModifiersTooltip} visits all nine slots and
	 * prints nothing at all for the ones without modifiers, so a slot seen on its own says nothing
	 * about whether a heading was drawn. The {@code addTooltip} call is the point where a line really
	 * reaches the tooltip, so it is what records the group.
	 *
	 * <p>Our own lines are appended after every group, which means they land in whichever one vanilla
	 * finished on — the trailing {@code When worn:} on three pieces of armour, but {@code When on
	 * Feet:} on boots, where no attribute modifier ever opens it. Knowing the last group lets us open
	 * our own heading in that case instead of slipping the damage lines into the slot's block.
	 *
	 * <p>The method is named {@code method_57370} because 1.21.11's mappings do not carry a name for
	 * it, so the identifier here is whatever the obfuscated jar had. Should a future version give it
	 * one, this handler stops matching and {@code check-mixins.py} says so; it will not fail quietly.
	 */
	@Inject(
			method = "lambda$addAttributeTooltips$0",
			at = @At("HEAD")
	)
	private static void noteAttributeGroup(
			MutableBoolean heading,
			Consumer<Component> lines,
			EquipmentSlotGroup group,
			Player player,
			Holder<Attribute> attribute,
			AttributeModifier modifier,
			ItemAttributeModifiers.Display display,
			CallbackInfo info) {
		if (display != ItemAttributeModifiers.Display.hidden() && heading.isTrue()) {
			AttributeTooltipContext.noteGroup(group);
		}
	}

	/**
	 * Adds one line per damage kind once the attribute block is done.
	 *
	 * <p>The method has two returns: an early one when the tooltip display component declines to show
	 * the attribute block at all, and the real one after the slot loop. {@code ordinal = 1} is the
	 * latter, so these lines appear whether or not vanilla printed its own, and the sharpness folded into
	 * the attack damage line is repeated here only when that line never existed.
	 *
	 * <p><b>The pass is closed here, in a {@code finally}, and nowhere else.</b> It used to be closed
	 * by a second handler of its own on {@code appendAttributeModifiersTooltip}, and that handler read
	 * back empty every time: on all seven items whose tooltip vanilla ends with a group, a second
	 * heading was printed under the first, and on a sword and a spear the folded sharpness was repeated
	 * on a line of its own even though the fold had plainly happened. Both are the same thing —
	 * {@code lastGroup} and {@code sawAttackDamage} were already reset by the time this ran, which
	 * happens exactly when {@code end()} has been reached first.
	 *
	 * <p>Two handlers on one method means depending on the order Mixin applies them, and it is not
	 * visible in either handler. The state belongs to whoever reads it, so it is closed on the same
	 * path that opens the pass, after the last line has gone out. {@code begin()} resets everything at
	 * the head of the method, so a pass that leaves by the early return has nothing left to clean.
	 *
	 * <p>1.21.11 inserted a {@link TooltipDisplay} between the consumer and the player.
	 */
	@Inject(
			method = "addAttributeTooltips",
			at = @At(value = "RETURN", ordinal = 1)
	)
	private void addEnchantmentStats(
			Consumer<Component> lines,
			TooltipDisplay display,
			Player player,
			CallbackInfo info) {
		try {
			EnchantmentTooltipConfiguration configuration = Configuration.instance.enchantment.tooltip;

			if (!configuration.enabled) {
				return;
			}

			List<EnchantmentStats.Stat> stats = EnchantmentStats.collect((ItemStack) (Object) this);
			EquipmentSlotGroup open = AttributeTooltipContext.lastGroup();

			if (!AttributeTooltipContext.sawAttackDamage()) {
				float bonus = (float) EnchantmentStats.sharpnessBonus((ItemStack) (Object) this);

				if (bonus != 0.0F) {
					// Sharpness declares slots: ["mainhand"], so the folded line belongs to the hand group
					open = heading(lines, open, EquipmentSlotGroup.MAINHAND);
					lines.accept(Component.translatable("tooltip.limitless.attack_damage",
							EnchantmentStats.format(bonus)).withStyle(STAT_COLOR));
				}
			}

			for (EnchantmentStats.Stat stat : stats) {
				open = heading(lines, open, stat.group());
				lines.accept(Component.translatable(stat.key(), EnchantmentStats.render(stat)).withStyle(STAT_COLOR));
			}
		} finally {
			AttributeTooltipContext.end();
		}
	}

	/**
	 * Opens a heading when the group changes, and answers the group now left open.
	 *
	 * <p>The first line answers whether to print at all: vanilla has already closed whatever block it
	 * was drawing, and a line added after it belongs to the group it finished on. When that is the
	 * group the line belongs to, no heading is needed and the line simply continues it — which is the
	 * case on three pieces of armour, where vanilla ends on {@code When worn:} and so do we.
	 *
	 * <p>Boots are the exception that made this necessary: nothing vanilla draws there opens
	 * {@code When worn:}, so the last block is {@code When on Feet:}. Without this the damage lines
	 * would be listed among armor, toughness and movement efficiency, under a heading about the wrong
	 * thing. With it they get a block of their own, like on the other three pieces.
	 *
	 * <p>The blank line and the grey heading are exactly what vanilla prints, so that a stack with our
	 * extra lines cannot be told from a stack without them apart from the lines themselves.
	 */
	private static EquipmentSlotGroup heading(Consumer<Component> lines, EquipmentSlotGroup open, EquipmentSlotGroup group) {
		String key = AttributeTooltipContext.heading(group);

		if (key == null || group == open) {
			return group;
		}

		lines.accept(CommonComponents.EMPTY);
		lines.accept(Component.translatable(key).withStyle(ChatFormatting.GRAY));

		return group;
	}
}