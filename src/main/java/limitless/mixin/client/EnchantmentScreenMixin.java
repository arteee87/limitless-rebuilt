package limitless.mixin.client;

import java.util.List;
import limitless.LimitlessLogger;
import org.spongepowered.asm.mixin.Unique;
import limitless.config.Configuration;
import limitless.config.enchantment.entry.normalization.EnchantmentNormalizationEntry;
import limitless.enchantment.ExperienceUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.EnchantmentScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enchanting table screen.
 *
 * <p>Shows the level a player really pays next to the level the table asks for, and optionally
 * lists the enchantments the table would offer instead of the mystery phrase.
 */
@Environment(EnvType.CLIENT)
@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenMixin extends HandledScreen<EnchantmentScreenHandler> {
	protected EnchantmentScreenMixin(EnchantmentScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
	}

	private static final int COLOR_ENABLED = 13172623;
	private static final int OUTLINE_ENABLED = 2957570;
	private static final int COLOR_DISABLED = 9199709;
	private static final int OUTLINE_DISABLED = 4666671;

	/** Set once per screen so the requirement is logged on open rather than on every frame. */
	@Unique
	private boolean limitlessLoggedRequirement;

	/**
	 * Bounds the level requirement the screen itself checks.
	 *
	 * <p>The screen never asks the server before offering an option: it reads
	 * {@code enchantmentPower[slot]} twice in {@code render} and once in {@code drawBackground} to
	 * compare against {@code experienceLevel}, to print "Level Requirement: %s", and to pick the
	 * greyed-out button texture. Capping only {@code EnchantmentScreenHandler#onButtonClick} is
	 * therefore not enough — the click is never sent, because the client already refused the option.
	 *
	 * <p>The value shown as the enchantment level comes from {@code enchantmentLevel}, a different
	 * array, so what the table offers is unaffected. {@code doTick} also reads the array, but only to
	 * test it for zero while animating the page turn, and clamping there would break that animation
	 * at a limit of zero.
	 */
	@Redirect(
			method = "render",
			at = @At(value = "FIELD", target = "Lnet/minecraft/screen/EnchantmentScreenHandler;enchantmentPower:[I", ordinal = 0)
	)
	private int[] limitless_renderRequirement(EnchantmentScreenHandler handler) {
		return cappedRequirement(handler);
	}

	@Redirect(
			method = "render",
			at = @At(value = "FIELD", target = "Lnet/minecraft/screen/EnchantmentScreenHandler;enchantmentPower:[I", ordinal = 1)
	)
	private int[] limitless_renderRequirementText(EnchantmentScreenHandler handler) {
		return cappedRequirement(handler);
	}

	@Redirect(
			method = "drawBackground",
			at = @At(value = "FIELD", target = "Lnet/minecraft/screen/EnchantmentScreenHandler;enchantmentPower:[I")
	)
	private int[] limitless_backgroundRequirement(EnchantmentScreenHandler handler) {
		return cappedRequirement(handler);
	}

	private int[] cappedRequirement(EnchantmentScreenHandler handler) {
		return ExperienceUtil.capRequiredLevels(
				handler.enchantmentPower,
				Configuration.instance.enchantment.maxLevelRequirement);
	}

	/** Reports the requirement of every slot once the screen opens, to confirm the cap took hold. */
	@Inject(method = "render", at = @At("HEAD"))
	private void logRequirement(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo info) {
		if (this.limitlessLoggedRequirement || !Configuration.instance.debug || client.player == null) {
			return;
		}

		this.limitlessLoggedRequirement = true;
		int limit = Configuration.instance.enchantment.maxLevelRequirement;

		for (int slot = 0; slot < handler.enchantmentPower.length; slot++) {
			int offered = handler.enchantmentPower[slot];
			int required = ExperienceUtil.capRequiredLevel(offered, limit);

			LimitlessLogger.debug("slot %d: offered level=%d, required=%d (limit %d), player has %d -> %s"
					.formatted(
							slot,
							offered,
							required,
							limit,
							client.player.experienceLevel,
							client.player.experienceLevel >= required ? "affordable" : "NOT affordable"));
		}
	}

	@Redirect(
			method = "render",
			at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 0)
	)
	private boolean revealEnchantments(List<Object> lines, Object clue) {
		if (!Configuration.instance.enchantment.revealEnchantments
				|| client.player == null
				|| client.world == null) {
			lines.add(clue);
			return true;
		}

		DynamicRegistryManager registries = client.world.getRegistryManager();
		Registry<Enchantment> registry = registries.getOrThrow(RegistryKeys.ENCHANTMENT);
		int found = 0;

		for (int slot = 0; slot < handler.enchantmentPower.length; slot++) {
			int id = handler.enchantmentId[slot];
			int level = handler.enchantmentLevel[slot];

			// the handler stores -1 in both for a slot the table did not fill
			if (id < 0 || level < 0) {
				continue;
			}

			// the ids come from this side's own registry, which method_17411 fills on the client as
			// well, so the lookup has to resolve; the guard only covers a desynced raw id
			Enchantment enchantment = registry.get(id);

			if (enchantment != null) {
				lines.add(found++, Enchantment.getName(registry.getEntry(enchantment), level));
			}
		}

		if (found == 0) {
			lines.add(clue);
		}

		return true;
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void showNormalizedCost(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo info) {
		EnchantmentNormalizationEntry normalization = Configuration.instance.enchantment.normalization;

		if (!normalization.enabled || client.player == null || client.player.getAbilities().creativeMode) {
			return;
		}

		int originX = (width - backgroundWidth) / 2;
		int originY = (height - backgroundHeight) / 2;

		for (int slot = 0; slot < handler.enchantmentPower.length; slot++) {
			int power = handler.enchantmentPower[slot];

			if (power <= 0 || client.player.experienceLevel <= Math.max(normalization.offset, power)) {
				continue;
			}

			int relative = ExperienceUtil.relativeCost(client.player, normalization.offset, slot + 1);
			String text = "(%d)".formatted(relative);
			int x = originX + 138;
			int y = originY + 19 * (slot + 1);

			boolean affordable = handler.getLapisCount() >= slot + 1;
			int color = affordable ? COLOR_ENABLED : COLOR_DISABLED;
			int outline = affordable ? OUTLINE_ENABLED : OUTLINE_DISABLED;

			context.drawText(client.textRenderer, text, x - 1, y, outline, false);
			context.drawText(client.textRenderer, text, x + 1, y, outline, false);
			context.drawText(client.textRenderer, text, x, y - 1, outline, false);
			context.drawText(client.textRenderer, text, x, y + 1, outline, false);
			context.drawText(client.textRenderer, text, x, y, color, false);
		}
	}
}