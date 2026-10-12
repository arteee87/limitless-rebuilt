package limitless_rebuilt.mixin.client;

import limitless_rebuilt.config.Configuration;
import limitless_rebuilt.config.anvil.entry.AnvilNormalizationEntry;
import limitless_rebuilt.config.common.CostDisplay;
import limitless_rebuilt.enchantment.ExperienceUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.ForgingScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Anvil screen.
 *
 * <p>Replaces the hard coded forty level threshold and, when anvil normalization is on, shows what
 * the operation really costs a player above level zero instead of the level it drops them to.
 */
@Environment(EnvType.CLIENT)
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin extends ForgingScreen<AnvilScreenHandler> {
	protected AnvilScreenMixin(
			AnvilScreenHandler handler,
			PlayerInventory inventory,
			Text title,
			Identifier texture) {
		super(handler, inventory, title, texture);
	}

	@ModifyConstant(method = "drawForeground", constant = @Constant(intValue = 40))
	private int modifyLevelLimit(int original) {
		return Configuration.instance.anvil.levelLimit;
	}

	@Redirect(
			method = "drawForeground",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/text/Text;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/text/MutableText;"
			)
	)
	// Text#translatable returns a MutableText; a redirect handler has to match the return type
	// exactly, otherwise the transformation of the whole class is aborted
	private MutableText showNormalizedCost(String key, Object... args) {
		AnvilNormalizationEntry normalization = Configuration.instance.anvil.normalization;

		if (normalization.enabled
				&& args.length > 0
				&& args[0] instanceof Integer cost
				&& client.player != null
				&& client.player.experienceLevel > cost) {
			int relative = ExperienceUtil.normalizedCost(client.player, cost);

			if (normalization.display == CostDisplay.REPLACE) {
				return Text.translatable(key, relative);
			}

			if (normalization.display == CostDisplay.APPEND) {
				Object[] copy = args.clone();
				copy[0] = args[0] + " (" + relative + ")";
				return Text.translatable(key, copy);
			}
		}

		return Text.translatable(key, args);
	}
}