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
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvironmentInterface;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

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
		// Both of these read out of the dynamic registries and both need a manager, on the server and
		// on the client alike. Neither has a no-argument variant: the configuration watcher reloads
		// the levels, because those live in a file, but the tooltip order lives in a tag that a file
		// cannot change.
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			RegistryAccess manager = server.registryAccess();
			EnchantmentLevels.reload(manager);
			EnchantmentStats.reload(manager);
		});
	}

	@Environment(EnvType.CLIENT)
	@Override
	public void onInitializeClient() {
		// CLIENT_STARTED fires before the client has a network handler, so the dynamic registries
		// are still empty there; JOIN fires once the world is loaded and works for an integrated
		// server as well. The client needs the same two reloads as the server, because it draws the
		// tooltips itself and never sees the integrated server's registry manager.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			RegistryAccess manager = client.getConnection().registryAccess();
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
	private static void reportConfigError(Minecraft client, Screen screen) {
		if (configErrorReported || !(screen instanceof TitleScreen) || Configuration.lastReadError() == null) {
			return;
		}

		configErrorReported = true;
		SystemToast.add(client.getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
				Component.translatable("toast.limitless-rebuilt.config_error.title"),
				Component.translatable("toast.limitless-rebuilt.config_error.description"));
	}

	/**
	 * Reproduces, once per world join, what the enchanting table does when it asks for options.
	 *
	 * <p>The table only ever offers enchantments on the client, so a session log says nothing about
	 * whether the client actually got the registry and the level limits; this prints both, next to the
	 * result of the very call that decides the contents of the three lapis slots.
	 */
	@Environment(EnvType.CLIENT)
	private static void selfCheck(RegistryAccess manager) {
		Registry<Enchantment> registry = manager.lookupOrThrow(Registries.ENCHANTMENT);
		List<Holder<Enchantment>> candidates = new ArrayList<>();
		registry.getTagOrEmpty(EnchantmentTags.IN_ENCHANTING_TABLE).forEach(candidates::add);

		LimitlessLogger.debug("client self check: %d enchantments, %d limits, %d table candidates"
				.formatted(registry.keySet().size(), EnchantmentLimits.size(), candidates.size()));

		for (ItemStack stack : List.of(new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.BOOK))) {
			for (int power : new int[]{5, 30}) {
				List<EnchantmentInstance> entries =
						EnchantmentHelper.getAvailableEnchantmentResults(power, stack, candidates.stream());
				LimitlessLogger.debug("  power %d on %s -> %d %s"
						.formatted(power, stack.getItem(), entries.size(), EnchantmentUtil.describe(entries)));
			}
		}

		registry.entrySet().stream()
				.filter(entry -> entry.getKey().identifier().toString().endsWith("sharpness"))
				.findFirst()
				.ifPresent(entry -> {
					Enchantment sharpness = entry.getValue();
					LimitlessLogger.debug("  sharpness: minLevel=%d maxLevel=%d minPower(1)=%d minPower(30)=%d"
							.formatted(
									sharpness.getMinLevel(),
									sharpness.getMaxLevel(),
									sharpness.getMinCost(sharpness.getMinLevel()),
									sharpness.getMinCost(30)));
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
