package limitless_rebuilt;

import net.minecraft.item.ItemStack;

/**
 * The stack whose attribute tooltip is being built right now.
 *
 * <p>Until 1.21.4 an {@code ItemStack} method drew the attribute lines itself, so the sharpness bonus
 * could be folded in by redirecting a call inside that class and still reaching the stack through
 * {@code this}. 1.21.11 moved the drawing into
 * {@code AttributeModifiersComponent.Display.Default}, which vanilla hands only the player, the
 * attribute and the modifier. That class has no stack to ask, and the bonus cannot be computed without
 * one, so the two sides are tied together here for the duration of a single tooltip.
 *
 * <p>Lines are built synchronously on the client render thread, which is why this is a plain static
 * rather than a {@link ThreadLocal}: {@link #begin} resets everything and {@link #end} clears it.
 */
public final class AttributeTooltipContext {
	private static ItemStack stack;
	private static boolean sawAttackDamage;

	private AttributeTooltipContext() {
	}

	/** Opens a tooltip pass for {@code stack}, discarding anything a previous pass left behind. */
	public static void begin(ItemStack stack) {
		AttributeTooltipContext.stack = stack;
		AttributeTooltipContext.sawAttackDamage = false;
	}

	/** Closes the pass. The early return of the tooltip method is a pass like any other and is covered. */
	public static void end() {
		stack = null;
		sawAttackDamage = false;
	}

	/** The stack being rendered, or {@code null} outside a tooltip pass. */
	public static ItemStack stack() {
		return stack;
	}

	/** Whether vanilla printed an attack damage line that the sharpness bonus was folded into. */
	public static boolean sawAttackDamage() {
		return sawAttackDamage;
	}

	/** Called by the fold itself, once it has seen the base attack damage modifier. */
	public static void markAttackDamage() {
		sawAttackDamage = true;
	}
}