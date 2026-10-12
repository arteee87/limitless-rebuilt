package limitless_rebuilt.enchantment;

import net.minecraft.entity.player.PlayerEntity;

/**
 * Level/experience arithmetic that mirrors how the vanilla enchanting and anvil costs are charged.
 *
 * <p>Vanilla grows the experience requirement per level super-linearly, so a player at level 100
 * pays far more than a player at level 30 for the same enchantment. Everything here is written so
 * that a player above {@code offset} pays exactly what a player of {@code offset} would pay.
 *
 * <p>Ports the 1.19.3 implementation of the mod verbatim.
 *
 * <p>The experience has to be handed to {@link PlayerEntity#addExperience(int)} and not written
 * into the fields directly: that method carries the level along, since 1.21.4 keeps
 * {@code experienceLevel} as the authoritative value and recomputes it from
 * {@code experienceProgress} only inside {@code addExperienceLevels}. An earlier port called a
 * local setter that wrote {@code experienceProgress} and {@code totalExperience} alone, which left
 * the level untouched — and since {@code PlayerEntityMixin} redirects the level write of
 * {@code applyEnchantmentCosts} to this method, a player above the offset simply never paid.
 */
public final class ExperienceUtil {
	/**
	 * Highest number of experience levels the enchanting table ever demands.
	 *
	 * <p>Vanilla computes {@code max(level, bookshelfCount * 2)} and counts at most fifteen
	 * bookcases, so {@code 15 * 2} is what it can ever reach.
	 */
	public static final int VANILLA_MAX_REQUIREMENT = 30;

	private ExperienceUtil() {
	}

	/**
	 * Bounds the level requirement the enchanting table checks against.
	 *
	 * <p>The setting is capped at the vanilla ceiling as well: raising it past thirty would make the
	 * table ask for more levels than vanilla ever does, which is the one thing the mod is not meant to
	 * change here. A limit of zero or less collapses the requirement to nothing, which leaves the
	 * separate {@code experienceLevel < id + 1} check that vanilla performs untouched — the table
	 * still costs its one, two or three levels.
	 */
	public static int capRequiredLevel(int enchantmentPower, int limit) {
		return Math.min(enchantmentPower, Math.max(0, Math.min(limit, VANILLA_MAX_REQUIREMENT)));
	}

	/**
	 * Bounded copy of the per-slot level array.
	 *
	 * <p>The client screen reads {@code enchantmentPower} itself to grey out an option it cannot pay
	 * for and to print "Level Requirement: %s", while the server reads it in {@code onButtonClick}. A
	 * copy is returned rather than the original being clamped in place, because the same array is what
	 * the generator uses as the enchantment level, and that must stay untouched.
	 */
	public static int[] capRequiredLevels(int[] powers, int limit) {
		int[] capped = new int[powers.length];

		for (int slot = 0; slot < powers.length; slot++) {
			capped[slot] = capRequiredLevel(powers[slot], limit);
		}

		return capped;
	}

	private static long currentExperience(PlayerEntity player) {
		return normalizedExperience(player.experienceLevel)
				+ (long) ((float) toNextLevel(player.experienceLevel) * player.experienceProgress);
	}

	/**
	 * Adds (or removes) {@code levels} relative to {@code offset}, keeping the partial experience
	 * bar consistent so that the player ends up with the same amount of experience a player of
	 * level {@code offset} would have.
	 */
	public static void addExperienceLevelsRelatively(PlayerEntity player, int offset, int levels) {
		if (player.experienceLevel <= offset) {
			player.addExperienceLevels(levels);
		} else if (levels > 0) {
			for (int level = offset - levels; level != offset; level++) {
				player.addExperience((int) fromPreviousLevel(level));
			}
		} else {
			for (int level = levels + offset; level != offset; level++) {
				player.addExperience((int) -fromPreviousLevel(-level));
			}
		}
	}

	public static void addExperienceLevelsNormalized(PlayerEntity player, int levels) {
		addExperienceLevelsRelatively(player, 0, levels);
	}

	/** Number of levels the player would drop to after paying {@code levels}. */
	public static int relativeCost(PlayerEntity player, int offset, int levels) {
		int level = player.experienceLevel;
		return level > offset
				? level - toLevel(currentExperience(player) - difference(levels - offset, offset))
				: level - levels;
	}

	public static int normalizedCost(PlayerEntity player, int levels) {
		return relativeCost(player, 0, levels);
	}

	public static long normalizedDifference(int from, int to) {
		if (from < 0) {
			from = -from;
		}

		if (to < 0) {
			to = -to;
		}

		return from > to ? difference(from - to, 0) : difference(0, to - from);
	}

	public static long normalizedExperience(int level, double progress) {
		return normalizedExperience(level) + (long) (progress * (double) normalizedExperience(level + 1));
	}

	public static long normalizedExperience(int level) {
		return difference(0, level);
	}

	private static long fromPreviousLevel(int level) {
		return difference(level - 1, level);
	}

	private static long toNextLevel(int level) {
		return difference(level, level + 1);
	}

	/**
	 * Experience that separates two levels, using the vanilla 0-15, 15-30 and 30+ curves. The
	 * 30+ segment is linear, which is what makes costs above level 30 proportional.
	 */
	private static long difference(int from, int to) {
		if (from == to) {
			return 0L;
		}

		if (from < 0) {
			from = -from;
		}

		if (to < 0) {
			to = -to;
		}

		if (from > to) {
			return difference(to, from);
		}

		long total = 0L;

		if (from < 15) {
			int toMin = Math.min(to, 15);
			long levels = (long) (toMin - from);
			total = levels * (long) (from + toMin + 6);

			if (to <= 15) {
				return total;
			}
		}

		if (from < 30) {
			int fromMax = Math.max(from, 15);
			int toMin = Math.min(to, 30);
			long levels = (long) (toMin - fromMax);
			total += 5L * (levels * (long) (fromMax + toMin - 1) / 2L) - 38L * levels;

			if (to <= 30) {
				return total;
			}
		}

		from = Math.max(from, 30);
		long levels = (long) (to - from);
		return total + 9L * (levels * (long) (from + to - 1) / 2L) - 158L * levels;
	}

	private static int toLevel(long experience) {
		if (experience == 0L) {
			return 0;
		}

		if (experience < 0L) {
			return -toLevel(-experience);
		}

		for (int level = 1; level < Integer.MAX_VALUE; level++) {
			if (normalizedExperience(level) > experience) {
				return level - 1;
			}
		}

		return 0;
	}
}