package limitless.config.enchantment.entry;

import limitless.config.enchantment.entry.radius.Radius;

/** Enchantment particles rendered above the table. */
public final class EnchantmentParticleConfiguration {
	public boolean enabled = true;
	public boolean inherit = true;
	public Radius radius = new Radius();
}