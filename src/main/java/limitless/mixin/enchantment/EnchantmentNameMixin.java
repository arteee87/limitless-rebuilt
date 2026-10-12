package limitless.mixin.enchantment;

import limitless.enchantment.RomanNumerals;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Renders enchantment levels as computed roman numerals.
 *
 * <p>Vanilla appends the level as {@code enchantment.level.<n>}, a key that {@code en_us.json}
 * defines for {@code 1} to {@code 10} only. A level 14 therefore shows up as the literal key
 * "Protection enchantment.level.14" in the item tooltip, in the enchanting table and on the anvil,
 * because all three go through {@link Enchantment#getName(RegistryEntry, int)}.
 *
 * <p>Ship enough language entries instead is not an option: the configured maximum level is not
 * bounded, so there is no highest key to enumerate.
 */
@Mixin(Enchantment.class)
public abstract class EnchantmentNameMixin {
	private static final String LEVEL_KEY = "enchantment.level.";

	/**
	 * The single {@link Text#translatable(String)} call inside {@code getName}, reached through an
	 * {@code invokedynamic} that concatenates the prefix with the level.
	 */
	@Redirect(
			method = "getName",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/text/Text;translatable(Ljava/lang/String;)Lnet/minecraft/text/MutableText;"
			)
	)
	private static MutableText limitless_levelNumeral(String key) {
		if (key.startsWith(LEVEL_KEY)) {
			try {
				return Text.literal(RomanNumerals.of(Integer.parseInt(key.substring(LEVEL_KEY.length()))));
			} catch (NumberFormatException ignored) {
				// not a level key, fall through to the original behaviour
			}
		}

		return Text.translatable(key);
	}
}