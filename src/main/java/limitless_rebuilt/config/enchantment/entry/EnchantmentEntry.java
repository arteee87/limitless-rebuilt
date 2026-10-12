package limitless_rebuilt.config.enchantment.entry;

import java.util.Objects;

/** Maximum level override for a single enchantment. */
public final class EnchantmentEntry implements Comparable<EnchantmentEntry> {
	public String identifier;
	public int maxLevel;
	public boolean useGlobalMaxLevel;

	public EnchantmentEntry() {
	}

	public EnchantmentEntry(String identifier, int maxLevel) {
		this.identifier = identifier;
		this.maxLevel = maxLevel;
	}

	@Override
	public int compareTo(EnchantmentEntry other) {
		return this.identifier.compareTo(other.identifier);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof EnchantmentEntry entry && Objects.equals(entry.identifier, this.identifier);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(this.identifier);
	}

	@Override
	public String toString() {
		return "%s -> %d".formatted(this.identifier, this.maxLevel);
	}
}