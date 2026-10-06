package net.antwire.fission.nuclear;

import net.minecraft.world.level.Level;

/** Units and conversions shared by the whole mod. */
public final class Nuclear {
	/** Game seconds per tick: a Minecraft day (24 000 ticks) is 24 hours. */
	public static final double GAME_SECONDS_PER_TICK = 3.6;
	/** Game seconds per real second. */
	public static final double GAME_SECONDS_PER_SECOND = 72;
	/** Joules per fission (about 200 MeV). */
	public static final double JOULES_PER_FISSION = 3.204e-11;
	/**
	 * A dose rate of 1 Sv per game hour, in the Radiation mod's rads (rem) per real second: 100 rem per sievert over the
	 * 50 real seconds of a game hour.
	 */
	public static final double RADS_PER_SV_H = 2.0;
	/** Nominal thermal power of one fuel channel block, watts. */
	public static final double CHANNEL_WATTS = 1.0e6;

	private Nuclear() {
	}

	public static double gameSeconds(Level level) {
		return level.getGameTime() * GAME_SECONDS_PER_TICK;
	}

	public static double gameSeconds(long gameTime) {
		return gameTime * GAME_SECONDS_PER_TICK;
	}

	/** Eta: neutrons per thermal neutron absorbed in fuel with these atom fractions of U-235 and Pu-239 (the rest U-238). */
	public static double eta(double u235, double pu239) {
		double u238 = Math.max(0, 1 - u235 - pu239);
		return (2.43 * 585 * u235 + 2.87 * 748 * pu239) / (681 * u235 + 1011 * pu239 + 2.7 * u238);
	}
}
