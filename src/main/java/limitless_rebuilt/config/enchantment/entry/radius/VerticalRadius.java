package limitless_rebuilt.config.enchantment.entry.radius;

/** Vertical distance from the enchanting table; negative values point downwards. */
public final class VerticalRadius {
	public int min = -8;
	public int max = 8;

	@Override
	public String toString() {
		return "[%d..%d]".formatted(this.min, this.max);
	}
}