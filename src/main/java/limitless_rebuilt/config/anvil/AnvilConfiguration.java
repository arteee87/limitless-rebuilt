package limitless_rebuilt.config.anvil;

import limitless_rebuilt.config.anvil.entry.AnvilNormalizationEntry;
import limitless_rebuilt.config.common.CostDisplay;

/** Anvil behaviour. */
public final class AnvilConfiguration {
	/** Apply the enchantment even if it conflicts with the one already on the item. */
	public boolean mergeConflicts = true;
	/** Apply the enchantment even if the item is not normally an acceptable target. */
	public boolean mergeIncompatible;
	/** Repair and enchantment costs never increase. */
	public boolean fixedCost = true;
	public int levelLimit = Integer.MAX_VALUE;
	public AnvilNormalizationEntry normalization = new AnvilNormalizationEntry();

	public void postLoad() {
		if (this.normalization == null) {
			this.normalization = new AnvilNormalizationEntry();
		}

		if (this.normalization.display == null) {
			this.normalization.display = CostDisplay.REPLACE;
		}
	}
}