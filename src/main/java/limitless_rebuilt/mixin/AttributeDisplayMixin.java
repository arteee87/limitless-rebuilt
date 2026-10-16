package limitless_rebuilt.mixin;

import limitless_rebuilt.AttributeTooltipContext;
import limitless_rebuilt.config.Configuration;
import limitless_rebuilt.enchantment.EnchantmentStats;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Folds the sharpness bonus into the attack damage line.
 *
 * <p>Vanilla already adds the player's own attack damage to the item's modifier before printing it, so
 * adding sharpness to that same value is what turns "When in Main Hand: +8 Attack Damage" into the
 * number the weapon really hits for.
 *
 * <p>1.21.4 drew these lines in {@code ItemStack#appendAttributeModifierTooltip}, so the mod could
 * redirect {@code value()} there and reach the stack through {@code this}. 1.21.11 moved the drawing
 * into {@code AttributeModifiersComponent.Display.Default}, which is why this is a mixin of its own:
 * the target is a different class, and {@code this} there is a stateless record rather than a stack.
 * The stack comes from {@link AttributeTooltipContext} instead, which {@code ItemStackMixin} opens for
 * the duration of the tooltip.
 *
 * <p>Only {@code Display.Default} is redirected, and that is what vanilla items use. A datapack that
 * replaces the display with {@code Display.Override} prints its own text and folds nothing;
 * {@code ItemStackMixin} sees through {@link AttributeTooltipContext#sawAttackDamage()} that no line was
 * folded and prints sharpness on a line of its own, so nothing goes missing.
 *
 * <p>The modifier is recognised by its identifier rather than by its attribute, which is how the
 * surrounding code picks it out too.
 */
@Mixin(AttributeModifiersComponent.Display.Default.class)
public class AttributeDisplayMixin {
	@Redirect(
			method = "addTooltip",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/attribute/EntityAttributeModifier;value()D"
			)
	)
	private double foldSharpness(EntityAttributeModifier modifier) {
		double value = modifier.value();

		ItemStack stack = AttributeTooltipContext.stack();

		// a null stack means vanilla asked for a line outside a stack tooltip, where there is nothing
		// to compute a bonus against
		if (stack != null
				&& modifier.idMatches(Item.BASE_ATTACK_DAMAGE_MODIFIER_ID)
				&& Configuration.instance.enchantment.tooltip.enabled) {
			value += EnchantmentStats.sharpnessBonus(stack);
			AttributeTooltipContext.markAttackDamage();
		}

		return value;
	}
}