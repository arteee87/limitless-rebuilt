package limitless.config.enchantment.entry.radius;

/** Horizontal distance from the enchanting table, counted on both axes. */
public final class HorizontalRadius {
	public int min = 0;
	public int max = 8;

	@Override
	public String toString() {
		return "[%d..%d]".formatted(this.min, this.max);
	}
}