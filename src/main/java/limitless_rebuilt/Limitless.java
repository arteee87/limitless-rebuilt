package limitless_rebuilt;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import limitless_rebuilt.enchantment.EnchantmentLimits;
import limitless_rebuilt.enchantment.EnchantmentUtil;
import limitless_rebuilt.config.Configuration;
import limitless_rebuilt.config.gui.LimitlessConfigScreen;
import limitless_rebuilt.enchantment.EnchantmentLevels;
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
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.gui.screen.Screen;

@EnvironmentInterface(value = EnvType.CLIENT, itf = ClientModInitializer.class)
public final class Limitless implements ModInitializer, ClientModInitializer {
	public static final String ID = "limitless-rebuilt";

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
		ServerLifecycleEvents.SERVER_STARTING.register(server -> EnchantmentLevels.reload(server.getRegistryManager()));
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

			if (Configuration.instance.debug) {
				selfCheck(manager);
			}
		});
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
