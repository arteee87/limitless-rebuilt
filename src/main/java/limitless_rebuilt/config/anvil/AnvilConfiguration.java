package limitless_rebuilt.config.anvil;

import limitless_rebuilt.config.anvil.entry.AnvilNormalizationEntry;
import limitless_rebuilt.config.common.CostDisplay;

/** Anvil behaviour. */
public final class AnvilConfiguration {
	/** Apply the enchantment even if it conflicts with the one already on the item. */
	public boolean mergeConflicts = true;
	/** Apply the enchantment even if the item is not normally an acceptable target. */
	public boolean mergeIncompatible;
	/** Whether the calculated cost is capped by {@link #costLimit}. */
	public boolean costLimitEnabled = true;
	/** Vanilla shows "too expensive" when the cost exceeds this many levels. */
	public int levelLimit = Integer.MAX_VALUE;
	/** When {@link #costLimitEnabled} is on, a calculated cost above this value is replaced with it. */
	public int costLimit = 40;
	public AnvilNormalizationEntry normalization = new AnvilNormalizationEntry();

	/**
	 * The "too expensive" threshold actually used. The level limit is ignored while the cost cap is on,
	 * which is why the {@code level limit} field is greyed out in that case.
	 */
	public int effectiveLevelLimit() {
		return costLimitEnabled ? Integer.MAX_VALUE : levelLimit;
	}

	public void postLoad() {
		if (this.normalization == null) {
			this.normalization = new AnvilNormalizationEntry();
		}

		if (this.normalization.display == null) {
			this.normalization.display = CostDisplay.REPLACE;
		}

		if (this.costLimit < 0) {
			this.costLimit = 0;
		}
	}
}