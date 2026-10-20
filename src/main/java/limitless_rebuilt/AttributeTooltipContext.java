package limitless_rebuilt;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.ItemStack;

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
	/**
	 * Slots vanilla actually opens a group for.
	 *
	 * <p>{@code ItemStack} prints {@code item.modifiers.<slot>} ahead of every group, and the lang file
	 * defines that key for exactly these seven. {@code ANY}, {@code HAND}, {@code BODY} and
	 * {@code SADDLE} have none, so a line belonging to one of them is printed without a heading rather
	 * than with a key that would render as the raw identifier.
	 */
	private static final Set<EquipmentSlotGroup> HEADED = EnumSet.of(
			EquipmentSlotGroup.MAINHAND,
			EquipmentSlotGroup.OFFHAND,
			EquipmentSlotGroup.FEET,
			EquipmentSlotGroup.LEGS,
			EquipmentSlotGroup.CHEST,
			EquipmentSlotGroup.HEAD,
			EquipmentSlotGroup.ARMOR);

	private static ItemStack stack;
	private static boolean sawAttackDamage;
	private static EquipmentSlotGroup lastGroup;

	private AttributeTooltipContext() {
	}

	/** Opens a tooltip pass for {@code stack}, discarding anything a previous pass left behind. */
	public static void begin(ItemStack stack) {
		AttributeTooltipContext.stack = stack;
		AttributeTooltipContext.sawAttackDamage = false;
		AttributeTooltipContext.lastGroup = null;
	}

	/** Closes the pass. The early return of the tooltip method is a pass like any other and is covered. */
	public static void end() {
		stack = null;
		sawAttackDamage = false;
		lastGroup = null;
	}

	/** The stack being rendered, or {@code null} outside a tooltip pass. */
	public static ItemStack stack() {
		return stack;
	}

	/**
	 * The group vanilla left open, which is the one the {@code item.modifiers} heading belongs to.
	 *
	 * <p>Recorded from the lines vanilla actually printed rather than from the slots it walked: it
	 * visits all nine of them and prints nothing for the empty ones, so a slot seen on its own says
	 * nothing about whether a heading was drawn. A stack with no attribute modifiers at all answers
	 * {@code null}, and our own lines then open their first group themselves.
	 */
	public static EquipmentSlotGroup lastGroup() {
		return lastGroup;
	}

	/** Called once per attribute line vanilla prints. */
	public static void noteGroup(EquipmentSlotGroup slot) {
		lastGroup = slot;
	}

	/**
	 * The heading key for a slot, or {@code null} when vanilla has no heading for it.
	 *
	 * <p>Vanilla's own key is reused rather than a new one added: the wording is already translated
	 * into every language the game ships, and a second key would start out English-only.
	 */
	public static String heading(EquipmentSlotGroup slot) {
		return slot != null && HEADED.contains(slot) ? "item.modifiers." + slot.getSerializedName() : null;
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