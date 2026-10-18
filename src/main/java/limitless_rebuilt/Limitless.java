package limitless_rebuilt;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import limitless_rebuilt.enchantment.EnchantmentLimits;
import limitless_rebuilt.enchantment.EnchantmentUtil;
import limitless_rebuilt.config.Configuration;
import limitless_rebuilt.config.gui.LimitlessConfigScreen;
import limitless_rebuilt.enchantment.EnchantmentLevels;
import limitless_rebuilt.enchantment.EnchantmentStats;
import limitless_rebuilt.enchantment.EnchantingBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvironmentInterface;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;

@EnvironmentInterface(value = EnvType.CLIENT, itf = ClientModInitializer.class)
public final class Limitless implements ModInitializer, ClientModInitializer {
	public static final String ID = "limitless-rebuilt";

	/** The configuration error toast is shown once per session; {@code AFTER_INIT} fires per screen. */
	@Environment(EnvType.CLIENT)
	private static boolean configErrorReported;

	@Override
	public void onInitialize() {
		// a broken configuration file must not take the game down with it
		try {
			Configuration.load();
			EnchantingBlocks.reload();
			Configuration.watch();
		} catch (Throwable t) {
			LimitlessLogger.error("Failed to read the configuration, falling back to the defaults: " + t);
		}

		CommandRegistrationCallback.EVENT.register(Command.INSTANCE);
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			DynamicRegistryManager manager = server.getRegistryManager();
			EnchantmentLevels.reload(manager);
			EnchantmentStats.reload(manager);
		});
	}

	@Environment(EnvType.CLIENT)
	@Override
	public void onInitializeClient() {
		// CLIENT_STARTED fires before the client has a network handler, so the dynamic registries
		// are still empty there; JOIN fires once the world is loaded and works for an integrated
		// server as well
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			DynamicRegistryManager manager = client.getNetworkHandler().getRegistryManager();
			EnchantmentLevels.reload(manager);
			EnchantmentStats.reload(manager);

			if (Configuration.instance.debug) {
				selfCheck(manager);
			}
		});

		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> reportConfigError(client, screen));
	}

	/**
	 * Tells the player once that the configuration file was not usable and the defaults are running.
	 *
	 * <p>A broken file no longer gets overwritten, so it stays broken until someone repairs it, and
	 * every symptom is silent: enchantments do not apply, the anvil behaves oddly, options read back
	 * as the wrong value. Nothing of that points at the configuration file, and the log line that
	 * says so is easy to miss, so it is repeated where it cannot be.
	 *
	 * <p>Raised from {@code TitleScreen} rather than from world entry, since the error is about the
	 * client having started at all and is worth showing before anything is loaded. The flag keeps it
	 * to once per session even though {@code AFTER_INIT} fires for every screen.
	 */
	@Environment(EnvType.CLIENT)
	private static void reportConfigError(MinecraftClient client, Screen screen) {
		if (configErrorReported || !(screen instanceof TitleScreen) || Configuration.lastReadError() == null) {
			return;
		}

		configErrorReported = true;
		SystemToast.add(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
				Text.translatable("toast.limitless-rebuilt.config_error.title"),
				Text.translatable("toast.limitless-rebuilt.config_error.description"));
	}

	/**
	 * Reproduces, once per world join, what the enchanting table does when it asks for options.
	 *
	 * <p>The table only ever offers enchantments on the client, so a session log says nothing about
	 * whether the client actually got the registry and the level limits; this prints both, next to the
	 * result of the very call that decides the contents of the three lapis slots.
	 */
	@Environment(EnvType.CLIENT)
	private static void selfCheck(DynamicRegistryManager manager) {
		Registry<Enchantment> registry = manager.getOrThrow(RegistryKeys.ENCHANTMENT);
		List<RegistryEntry<Enchantment>> candidates = new ArrayList<>();
		registry.iterateEntries(EnchantmentTags.IN_ENCHANTING_TABLE).forEach(candidates::add);

		LimitlessLogger.debug("client self check: %d enchantments, %d limits, %d table candidates"
				.formatted(registry.getIds().size(), EnchantmentLimits.size(), candidates.size()));

		for (ItemStack stack : List.of(new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.BOOK))) {
			for (int power : new int[]{5, 30}) {
				List<EnchantmentLevelEntry> entries =
						EnchantmentHelper.getPossibleEntries(power, stack, candidates.stream());
				LimitlessLogger.debug("  power %d on %s -> %d %s"
						.formatted(power, stack.getItem(), entries.size(), EnchantmentUtil.describe(entries)));
			}
		}

		registry.getEntrySet().stream()
				.filter(entry -> entry.getKey().getValue().toString().endsWith("sharpness"))
				.findFirst()
				.ifPresent(entry -> {
					Enchantment sharpness = entry.getValue();
					LimitlessLogger.debug("  sharpness: minLevel=%d maxLevel=%d minPower(1)=%d minPower(30)=%d"
							.formatted(
									sharpness.getMinLevel(),
									sharpness.getMaxLevel(),
									sharpness.getMinPower(sharpness.getMinLevel()),
									sharpness.getMinPower(30)));
				});
	}

	/** Mod Menu entry point; the screen class is only loaded on a client. */
	@Environment(EnvType.CLIENT)
	public static final class ModMenu implements ModMenuApi {
		@Override
		public ConfigScreenFactory<Screen> getModConfigScreenFactory() {
			return LimitlessConfigScreen::create;
		}
	}
}
