package limitless_rebuilt.config.enchantment.entry;

/**
 * Extra tooltip lines that account for what an enchantment actually does.
 *
 * <p>Sharpness and knockback live on the {@code minecraft:damage} and {@code minecraft:knockback}
 * channels and are applied on the hit, not as a constant attribute, so the attack damage line never
 * reflects them on its own — which is the whole reason this section exists.
 *
 * <p>Nothing is read from {@code attribute_effects}: vanilla prints those in its own block already,
 * with the value and the unit attached, so a second copy would only duplicate what is on screen.
 *
 * <p>The per-enchantment switches are by channel rather than by category, because that is what the
 * data actually groups on.
 */
public final class EnchantmentTooltipConfiguration {
	/**
	 * Declared on: a feature that ships switched off is indistinguishable from a broken one, and the
	 * missing lines look like the code never applied. A user who does not want them turns this off.
	 */
	public boolean enabled = true;
	public boolean showDamage = true;
	public boolean showKnockback = true;
	public boolean showProtection = true;
}