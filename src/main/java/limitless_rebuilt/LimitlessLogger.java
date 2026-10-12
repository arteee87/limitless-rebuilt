package limitless_rebuilt;

import java.io.PrintStream;
import java.time.LocalTime;
import limitless_rebuilt.config.Configuration;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;

public final class LimitlessLogger {
	/** Matches the mod id, so a log filter by logger name finds everything this mod prints. */
	private static final String NAME = "limitless-rebuilt";

	private static final TriConsumer<PrintStream, Level, String> print = isDevelopmentEnvironment()
			? (stream, level, message) -> stream.printf(
					"[%s] [%s/%s] (%s) %s%n",
					LocalTime.now().withNano(0), Thread.currentThread().getName(), level, NAME, message)
			: (stream, level, message) -> LogManager.getLogger(NAME).log(level, message);

	/**
	 * The configuration is read and written outside of a running game in tests, and the launcher
	 * is not there yet while mixins are still being applied; the logger must not depend on it.
	 */
	private static boolean isDevelopmentEnvironment() {
		try {
			return FabricLoader.getInstance().isDevelopmentEnvironment();
		} catch (Throwable ignored) {
			return false;
		}
	}

	private LimitlessLogger() {
	}

	public static void info(String message) {
		print.accept(System.out, Level.INFO, message);
	}

	public static void warn(String message) {
		print.accept(System.out, Level.WARN, message);
	}

	public static void error(String message) {
		print.accept(System.err, Level.ERROR, message);
	}

	/** Only printed when {@code debug} is on in the configuration. */
	public static void debug(String message) {
		if (Configuration.instance.debug) {
			info("[debug] " + message);
		}
	}

	public static void time(String message, Runnable action) {
		long start = System.nanoTime();
		action.run();
		info(message.formatted((double) (System.nanoTime() - start) / 1.0E9));
	}

	@FunctionalInterface
	private interface TriConsumer<A, B, C> {
		void accept(A a, B b, C c);
	}
}