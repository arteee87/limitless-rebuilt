package limitless.config.anvil.entry;

import limitless.config.common.CostDisplay;

/** Makes anvil cost take the same amount of experience a player of the given level would pay. */
public final class AnvilNormalizationEntry {
	public boolean enabled = true;
	public CostDisplay display = CostDisplay.REPLACE;
}