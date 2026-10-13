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
	/**
	 * Logs the internals; noisy, meant for tracking down behaviour.
	 *
	 * <p>Covers the enchanting table as well as the anvil. On the anvil it reports, per
	 * {@code updateResult}, the two input stacks, whether an enchantment was rejected as an invalid
	 * target or as conflicting and which setting let it through, and the resulting cost and output
	 * stack. It also reports the affordability gate on {@code canTakeOutput} and whether the client
	 * screen showed a normalized cost, since the two differ whenever the player is not above the
	 * cost and the shown number staying vanilla is otherwise unexplained.
	 *
	 * <p>Each of those points prints only when its message differs from the one before it. Two of
	 * them sit in the per frame path, so an unconditional print would write the log at frame rate;
	 * an anvil line is tagged with the side it came from, {@code [render]} or {@code [server]}, since
	 * the integrated server and the client each run the anvil handler.
	 *
	 * <p>Goes through {@link limitless_rebuilt.LimitlessLogger#debug}, so the messages land in the
	 * usual {@code latest.log}.
	 */
	public boolean debug;
	public EnchantmentConfiguration enchantment = new EnchantmentConfiguration();
	public AnvilConfiguration anvil = new AnvilConfiguration();

	public static volatile Configuration instance = new Configuration();

	private static final AtomicBoolean RELOADING = new AtomicBoolean();
	private static volatile FileTime mtime = FileTime.from(Instant.EPOCH);
	private static volatile String lastReadError;

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/** Reads the configuration from disk and publishes it as {@link #instance}. */
	public static void load() {
		Path file = file();

		try {
			instance = ConfigSerializer.read(file);
			lastReadError = null;
		} catch (ConfigReadException e) {
			// the file stays as it is; the user has to repair it, and a toast tells them to
			LimitlessLogger.error(e.getMessage());
			instance = ConfigSerializer.defaults();
			lastReadError = e.getMessage();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to load the limitless configuration.", e);
		}

		// on both paths: the watcher compares against this, and leaving it stale on a file it can
		// never read would make it reload once per second and bury the log
		mtime = lastModified(file);
	}

	/**
	 * Why the last {@link #load()} fell back to the defaults, or {@code null} when it did not.
	 *
	 * <p>Kept for the client, which raises a toast on the title screen from this: a configuration
	 * error that only reaches the log is easy to miss, because everything else in the mod then looks
	 * like it simply has no effect.
	 */
	public static String lastReadError() {
		return lastReadError;
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
		// The single point every write goes through, and the only place worth guarding. A broken
		// file is left alone on purpose, so writing the defaults back over it would undo that
		// from here: EnchantmentLevels writes the per enchantment entries it discovers, and the
		// configuration screen writes whatever the toggles currently say, and both would silently
		// destroy exactly the hand written file the user still has to repair.
		if (lastReadError != null) {
			LimitlessLogger.warn(("Not writing \"%s\": contents could not be read.%n"
					+ "Using defaults.%n"
					+ "Fix the file, or delete it for the mod to re-create automatically.%n"
					+ "Check the log for details.").formatted(file()));
			return;
		}

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