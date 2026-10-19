package limitless_rebuilt.config.anvil.entry;

import limitless_rebuilt.config.common.CostDisplay;

/** Makes anvil cost take the same amount of experience a player of the given level would pay. */
public final class AnvilNormalizationEntry {
	public boolean enabled = false;
	public CostDisplay display = CostDisplay.REPLACE;
}