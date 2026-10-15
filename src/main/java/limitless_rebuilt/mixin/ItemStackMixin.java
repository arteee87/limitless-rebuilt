package limitless_rebuilt.mixin;

import limitless_rebuilt.config.Configuration;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.util.List;
import java.util.function.Consumer;
import limitless_rebuilt.config.enchantment.entry.EnchantmentTooltipConfiguration;
import limitless_rebuilt.enchantment.EnchantmentStats;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

	/** Set while the attribute block runs, so the fallback line knows it has nothing to add to. */
	@Unique
	private boolean limitlessSawAttackDamage;

	/**
	 * Folds the sharpness bonus into the attack damage line.
	 *
	 * <p>Vanilla already adds the player's own attack damage to the item's modifier before printing
	 * it, so adding sharpness to that same value is what turns "When in Main Hand: +8 Attack Damage"
	 * into the number the weapon really hits for. Everything else has no attribute to land on and is
	 * added as its own line instead.
	 *
	 * <p>The modifier is recognised by its identifier rather than by its attribute, which is how the
	 * surrounding code picks it out too.
	 */
	@Redirect(
			method = "appendAttributeModifierTooltip",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/attribute/EntityAttributeModifier;value()D"
			)
	)
	private double foldSharpness(EntityAttributeModifier modifier) {
		double value = modifier.value();

		// a redirect over an instance call only hands over the receiver, and "this" is the stack
		// the tooltip is being built for
		if (modifier.idMatches(Item.BASE_ATTACK_DAMAGE_MODIFIER_ID)
				&& Configuration.instance.enchantment.tooltip.enabled) {
			value += EnchantmentStats.sharpnessBonus((ItemStack) (Object) this);
			this.limitlessSawAttackDamage = true;
		}

		return value;
	}

	/**
	 * Adds one line per damage kind once the attribute block is done.
	 *
	 * <p>The method has two returns: an early one when the component declines to show anything, and
	 * the real one. {@code ordinal = 1} is the latter, so these lines appear whether or not vanilla
	 * printed its own, and the sharpness folded into the attack damage line is repeated here only when
	 * that line never existed.
	 */
	@Inject(
			method = "appendAttributeModifiersTooltip",
			at = @At(value = "RETURN", ordinal = 1)
	)
	private void addEnchantmentStats(Consumer<Text> lines, PlayerEntity player, CallbackInfo info) {
		EnchantmentTooltipConfiguration configuration = Configuration.instance.enchantment.tooltip;

		if (!configuration.enabled) {
			return;
		}

		List<EnchantmentStats.Stat> stats = EnchantmentStats.collect((ItemStack) (Object) this);

		if (!this.limitlessSawAttackDamage) {
			float bonus = (float) EnchantmentStats.sharpnessBonus((ItemStack) (Object) this);

			if (bonus != 0.0F) {
				lines.accept(Text.translatable("tooltip.limitless.attack_damage",
						EnchantmentStats.format(bonus)).formatted(STAT_COLOR));
			}
		}

		for (EnchantmentStats.Stat stat : stats) {
			lines.accept(Text.translatable(stat.key(), EnchantmentStats.render(stat)).formatted(STAT_COLOR));
		}

		this.limitlessSawAttackDamage = false;
	}
}