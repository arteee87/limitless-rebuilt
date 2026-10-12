package limitless.mixin;

import com.mojang.serialization.Codec;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Removes the hard 255 level ceiling of {@link ItemEnchantmentsComponent}.
 *
 * <p>1.19.3 stored the level as a NBT {@code short} and the mod only had to redirect the two
 * {@code NbtCompound#putShort} calls that truncated it. 1.21.4 keeps the levels in a component and
 * guards them in three places that the original mod never had to deal with:
 *
 * <ol>
 *   <li>{@code ENCHANTMENT_LEVEL_CODEC = Codec.intRange(1, 255)} — rejects anything above 255 while
 *       reading an item from NBT, a save file or the network ("Tried to load invalid item");
 *   <li>the constructor, which throws {@code IllegalArgumentException} on a level above 255;
 *   <li>{@code Builder#set} and {@code Builder#add}, which clamp with {@code Math.min(level, 255)}.
 * </ol>
 *
 * <p>Without lifting all three, the configured maximum level is silently rounded down to 255 as soon
 * as an item is written or read, which is exactly the ceiling the mod exists to remove.
 *
 * @author auoeke
 * @reason the mod raises the enchantment level limit beyond what the component allows
 */
@Mixin(ItemEnchantmentsComponent.class)
public abstract class ItemEnchantmentsComponentMixin {
	@Redirect(
			method = "<clinit>",
			at = @At(
					value = "INVOKE",
					target = "Lcom/mojang/serialization/Codec;intRange(II)Lcom/mojang/serialization/Codec;"
			)
	)
	private static Codec<Integer> limitless_levelCodec(int min, int max) {
		return Codec.intRange(min, Integer.MAX_VALUE);
	}

	@ModifyConstant(method = "<init>", constant = @Constant(intValue = 255))
	private int limitless_constructorLimit(int original) {
		return Integer.MAX_VALUE;
	}
}