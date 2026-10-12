package limitless_rebuilt.config.enchantment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import limitless_rebuilt.config.enchantment.entry.radius.HorizontalRadius;
import limitless_rebuilt.config.enchantment.entry.radius.VerticalRadius;
import limitless_rebuilt.LimitlessLogger;
import limitless_rebuilt.config.enchantment.entry.radius.Radius;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

/**
 * Blocks that contribute enchanting power.
 *
 * <p>A block contributes {@code 2} (vanilla bookshelves contribute {@code 1}) when it is part of
 * {@code #c:enchanting_blocks} or {@code #c:bookshelves} and is not blacklisted; blocks in the
 * whitelist always contribute their configured amount, blacklists are irrelevant for them.
 */
public final class EnchantingBlockConfiguration {
	public static final String NAMESPACE = "c";
	public static final TagKey<net.minecraft.block.Block> ENCHANTING_BLOCKS =
			TagKey.of(RegistryKeys.BLOCK, Identifier.of(NAMESPACE, "enchanting_blocks"));
	public static final TagKey<net.minecraft.block.Block> BOOKSHELVES =
			TagKey.of(RegistryKeys.BLOCK, Identifier.of(NAMESPACE, "bookshelves"));

	public static final float DEFAULT_POWER = 2.0F;

	private static final String EXAMPLE_BLOCK = "examplemod:example_block";

	public Radius radius = new Radius();
	public int maxBlocks = 512;
	public int maxPower = 1024;
	public Map<String, Float> whitelist = new LinkedHashMap<>(Map.of(EXAMPLE_BLOCK, 5.0F));
	public List<String> blacklist = new ArrayList<>(List.of(EXAMPLE_BLOCK));

	/**
	 * Drops entries that are not valid identifiers and the placeholder entries shipped with the
	 * defaults, so that {@link limitless_rebuilt.enchantment.EnchantingBlocks} only sees real blocks, and
	 * swaps inverted radius bounds.
	 *
	 * <p>The scan loops run from {@code min} to {@code max}, so inverted bounds make them iterate
	 * zero times and the table reports no power at all, which reads exactly like a broken bookshelf
	 * scan rather than like a typo.
	 */
	public void postLoad() {
		Map<String, Float> validated = new LinkedHashMap<>();

		this.whitelist.forEach((key, power) -> {
			if (isValid(key, "white")) {
				validated.put(key, power == null ? DEFAULT_POWER : power);
			}
		});

		this.whitelist = validated;
		this.blacklist.removeIf(key -> !isValid(key, "black"));

		HorizontalRadius xz = this.radius.xz;

		if (xz.min > xz.max) {
			int min = xz.min;
			xz.min = xz.max;
			xz.max = min;
			LimitlessLogger.warn(
					"Enchanting block radius \"xz\" had min above max; the two were swapped.");
		}

		VerticalRadius y = this.radius.y;

		if (y.min > y.max) {
			int min = y.min;
			y.min = y.max;
			y.max = min;
			LimitlessLogger.warn(
					"Enchanting block radius \"y\" had min above max; the two were swapped.");
		}
	}

	private static boolean isValid(String key, String type) {
		if (key == null || Identifier.tryParse(key) == null) {
			LimitlessLogger.error(
					"Key \"%s\" listed in limitless' enchanting block %slist is not formatted correctly."
							.formatted(key, type));
			return false;
		}

		return !EXAMPLE_BLOCK.equals(key);
	}
}