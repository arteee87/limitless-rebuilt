package limitless.config.enchantment.entry;

/** How conflicts between enchantments are treated while generating them. */
public final class EnchantingConflicts {
	/** Generate several conflicting enchantments in a single lap instead of removing the losers. */
	public boolean generate;
	/** Merge the level of an already present conflicting enchantment instead of dropping it. */
	public boolean merge;
}