package limitless.config.enchantment.entry.radius;

/** Radius box used both for enchanting power scanning and for the enchantment particles. */
public final class Radius {
	public HorizontalRadius xz = new HorizontalRadius();
	public VerticalRadius y = new VerticalRadius();

	/** Radius configured elsewhere, used by the particle renderer when it inherits the box. */
	public static Radius copyOf(Radius other) {
		Radius copy = new Radius();
		copy.xz.min = other.xz.min;
		copy.xz.max = other.xz.max;
		copy.y.min = other.y.min;
		copy.y.max = other.y.max;
		return copy;
	}
}