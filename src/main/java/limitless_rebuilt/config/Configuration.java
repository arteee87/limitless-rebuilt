package limitless_rebuilt.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import limitless_rebuilt.LimitlessLogger;
import limitless_rebuilt.config.anvil.AnvilConfiguration;
import limitless_rebuilt.config.enchantment.EnchantmentConfiguration;
import limitless_rebuilt.enchantment.EnchantingBlocks;
import limitless_rebuilt.enchantment.EnchantmentLevels;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Root of {@code config/limitless.json}.
 *
 * <p>{@link #instance} is never null: mixins read it during class loading, long before the game
 * finishes booting.
 */
public final class Configuration {
	public static final String FILE_NAME = "limitless.json";
	private static final long WATCH_PERIOD_MILLIS = 1000L;

	public boolean automaticReloading;
	/** Logs the enchanting table internals; noisy, meant for tracking down behaviour. */
	public boolean debug;
	public EnchantmentConfiguration enchantment = new EnchantmentConfiguration();
	public AnvilConfiguration anvil = new AnvilConfiguration();

	public static volatile Configuration instance = new Configuration();

	private static final AtomicBoolean RELOADING = new AtomicBoolean();
	private static volatile FileTime mtime = FileTime.from(Instant.EPOCH);

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/** Reads the configuration from disk and publishes it as {@link #instance}. */
	public static void load() {
		Path file = file();

		try {
			instance = ConfigSerializer.read(file);
			mtime = lastModified(file);
			LimitlessLogger.info("Loaded configuration from \"%s\".".formatted(file));
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to load the limitless configuration.", e);
		}
	}

	/** Re-reads the file and re-applies every value derived from it. */
	public static void reload() {
		if (!RELOADING.compareAndSet(false, true)) {
			return;
		}

		try {
			LimitlessLogger.time("Finished reloading in %ss.", () -> {
				load();
				EnchantmentLevels.reload();
				EnchantingBlocks.reload();
			});
		} finally {
			RELOADING.set(false);
		}
	}

	public static void save() {
		try {
			Path file = file();
			ConfigSerializer.write(file, instance);
			mtime = lastModified(file);
		} catch (IOException e) {
			LimitlessLogger.error("Failed to write the configuration: %s".formatted(e.toString()));
		}
	}

	/** Watches the file and reloads it when it changes; polls once per second on a daemon thread. */
	public static void watch() {
		Timer timer = new Timer("limitless-config-watcher", true);

		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				if (instance.automaticReloading && mtime.compareTo(lastModified(file())) < 0) {
					reload();
				}
			}
		}, 0L, WATCH_PERIOD_MILLIS);
	}

	private static FileTime lastModified(Path file) {
		try {
			return Files.exists(file) ? Files.getLastModifiedTime(file) : FileTime.from(Instant.EPOCH);
		} catch (IOException e) {
			return FileTime.from(Instant.EPOCH);
		}
	}

	/**
	 * Fills in everything a hand written, older or partial configuration file may be missing, so
	 * that the rest of the mod never has to null check.
	 */
	public void postLoad() {
		if (this.enchantment == null) {
			this.enchantment = new EnchantmentConfiguration();
		}

		if (this.anvil == null) {
			this.anvil = new AnvilConfiguration();
		}

		this.enchantment.postLoad();
		this.anvil.postLoad();
	}
}