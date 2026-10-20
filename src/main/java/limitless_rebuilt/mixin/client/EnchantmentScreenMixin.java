package limitless_rebuilt.mixin.client;

import java.util.List;
import limitless_rebuilt.LimitlessLogger;
import org.spongepowered.asm.mixin.Unique;
import limitless_rebuilt.config.Configuration;
import limitless_rebuilt.config.enchantment.entry.normalization.EnchantmentNormalizationEntry;
import limitless_rebuilt.enchantment.ExperienceUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.enchantment.Enchantment;
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
public abstract class EnchantmentScreenMixin extends AbstractContainerScreen<EnchantmentMenu> {
	protected EnchantmentScreenMixin(EnchantmentMenu handler, Inventory inventory, Component title) {
		super(handler, inventory, title);
	}

	private static final int COLOR_ENABLED = 13172623;
	private static final int OUTLINE_ENABLED = 2957570;
	private static final int COLOR_DISABLED = 9199709;
	private static final int OUTLINE_DISABLED = 4666671;

	/** Set once per screen so the requirement is logged on open rather than on every frame. */
	@Unique
	private String limitlessLastRequirement;

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
			at = @At(value = "FIELD", target = "Lnet/minecraft/world/inventory/EnchantmentMenu;costs:[I", ordinal = 0)
	)
	private int[] limitless_renderRequirement(EnchantmentMenu handler) {
		return cappedRequirement(handler);
	}

	@Redirect(
			method = "render",
			at = @At(value = "FIELD", target = "Lnet/minecraft/world/inventory/EnchantmentMenu;costs:[I", ordinal = 1)
	)
	private int[] limitless_renderRequirementText(EnchantmentMenu handler) {
		return cappedRequirement(handler);
	}

	@Redirect(
			method = "renderBg",
			at = @At(value = "FIELD", target = "Lnet/minecraft/world/inventory/EnchantmentMenu;costs:[I")
	)
	private int[] limitless_backgroundRequirement(EnchantmentMenu handler) {
		return cappedRequirement(handler);
	}

	private int[] cappedRequirement(EnchantmentMenu handler) {
		return ExperienceUtil.capRequiredLevels(
				handler.costs,
				Configuration.instance.enchantment.maxLevelRequirement);
	}

	/**
	 * Reports the requirement of every slot, to confirm the cap took hold.
	 *
	 * <p>The guard used to be a plain printed-once flag, which made this blind for the rest of the
	 * session: swapping the item in the table re-offers completely different levels and nothing was
	 * printed. A stack the mod refuses to enchant offers zero in all three slots, and that is precisely
	 * the case worth reading — so the three lines are built first and compared, the same way the anvil
	 * screen compares its own.
	 */
	@Inject(method = "render", at = @At("HEAD"))
	private void logRequirement(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo info) {
		if (!Configuration.instance.debug || minecraft.player == null) {
			return;
		}

		int limit = Configuration.instance.enchantment.maxLevelRequirement;
		StringBuilder builder = new StringBuilder();

		for (int slot = 0; slot < menu.costs.length; slot++) {
			int offered = menu.costs[slot];
			int required = ExperienceUtil.capRequiredLevel(offered, limit);

			builder.append("slot %d: offered level=%d, required=%d (limit %d), player has %d -> %s%n"
					.formatted(
							slot,
							offered,
							required,
							limit,
							minecraft.player.experienceLevel,
							minecraft.player.experienceLevel >= required ? "affordable" : "NOT affordable"));
		}

		String message = builder.toString();

		if (!message.equals(this.limitlessLastRequirement)) {
			this.limitlessLastRequirement = message;
			LimitlessLogger.debug(message.stripTrailing());
		}
	}

	@Redirect(
			method = "render",
			at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 0)
	)
	private boolean revealEnchantments(List<Object> lines, Object clue) {
		if (!Configuration.instance.enchantment.revealEnchantments
				|| minecraft.player == null
				|| minecraft.level == null) {
			lines.add(clue);
			return true;
		}

		RegistryAccess registries = minecraft.level.registryAccess();
		Registry<Enchantment> registry = registries.lookupOrThrow(Registries.ENCHANTMENT);
		int found = 0;

		for (int slot = 0; slot < menu.costs.length; slot++) {
			int id = menu.enchantClue[slot];
			int level = menu.levelClue[slot];

			// the handler stores -1 in both for a slot the table did not fill
			if (id < 0 || level < 0) {
				continue;
			}

			// the ids come from this side's own registry, which method_17411 fills on the client as
			// well, so the lookup has to resolve; the guard only covers a desynced raw id
			Enchantment enchantment = registry.byId(id);

			if (enchantment != null) {
				lines.add(found++, Enchantment.getFullname(registry.wrapAsHolder(enchantment), level));
			}
		}

		if (found == 0) {
			lines.add(clue);
		}

		return true;
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void showNormalizedCost(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo info) {
		EnchantmentNormalizationEntry normalization = Configuration.instance.enchantment.normalization;

		if (!normalization.enabled || minecraft.player == null || minecraft.player.getAbilities().instabuild) {
			return;
		}

		int originX = (width - imageWidth) / 2;
		int originY = (height - imageHeight) / 2;

		for (int slot = 0; slot < menu.costs.length; slot++) {
			int power = menu.costs[slot];

			if (power <= 0 || minecraft.player.experienceLevel <= Math.max(normalization.offset, power)) {
				continue;
			}

			int relative = ExperienceUtil.relativeCost(minecraft.player, normalization.offset, slot + 1);
			String text = "(%d)".formatted(relative);
			int x = originX + 138;
			int y = originY + 19 * (slot + 1);

			boolean affordable = menu.getGoldCount() >= slot + 1;
			int color = affordable ? COLOR_ENABLED : COLOR_DISABLED;
			int outline = affordable ? OUTLINE_ENABLED : OUTLINE_DISABLED;

			context.drawString(minecraft.font, text, x - 1, y, outline, false);
			context.drawString(minecraft.font, text, x + 1, y, outline, false);
			context.drawString(minecraft.font, text, x, y - 1, outline, false);
			context.drawString(minecraft.font, text, x, y + 1, outline, false);
			context.drawString(minecraft.font, text, x, y, color, false);
		}
	}
}