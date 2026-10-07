package net.antwire.fission.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * The wind that carries a radioactive cloud. Minecraft has none, so it is made up, the same for everyone: each world has
 * a prevailing direction (from its seed) that swings slowly back and forth over the days and a little within hours;
 * 3 to 6 m/s, more in rain and most in thunderstorms. {@code /fission wind set} fixes it, e.g. to send a cloud somewhere.
 */
public final class Wind {
	/** Direction the wind blows towards, degrees clockwise from north (0 = north, 90 = east); NaN = natural wind. */
	static double fixedTowards = Double.NaN;
	static double fixedSpeed;

	private Wind() {
	}

	/** Degrees clockwise from north the wind blows towards. */
	public static double towards(ServerLevel level) {
		if (!Double.isNaN(fixedTowards)) {
			return fixedTowards;
		}
		long seed = level.getSeed();
		double base = Math.floorMod(seed ^ (seed >>> 29), 360);
		double t = level.getGameTime();
		return Math.floorMod((long) (base + 50 * Math.sin(t / 96000.0 * 2 * Math.PI) + 20 * Math.sin(t / 7000.0 * 2 * Math.PI + 1.3)), 360);
	}

	/** Metres (blocks) per second. */
	public static double speed(ServerLevel level) {
		if (!Double.isNaN(fixedTowards)) {
			return fixedSpeed;
		}
		double t = level.getGameTime();
		double v = 4.5 + 1.5 * Math.sin(t / 9000.0 * 2 * Math.PI + 0.7);
		if (level.isThundering()) {
			v += 6;
		} else if (level.isRaining()) {
			v += 3;
		}
		return v;
	}

	/** Blocks per tick. */
	public static Vec3 velocity(ServerLevel level) {
		double a = Math.toRadians(towards(level));
		double v = speed(level) / 20.0;
		// north is -z, east is +x
		return new Vec3(Math.sin(a) * v, 0, -Math.cos(a) * v);
	}

	public static void fix(double towardsDegrees, double metresPerSecond) {
		fixedTowards = Math.floorMod((long) Math.round(towardsDegrees), 360);
		fixedSpeed = Math.max(0, metresPerSecond);
	}

	public static void release() {
		fixedTowards = Double.NaN;
	}

	public static boolean fixed() {
		return !Double.isNaN(fixedTowards);
	}

	public static String describe(ServerLevel level) {
		double to = towards(level);
		return String.format(Locale.ROOT, "wind from the %s towards the %s (%.0f°), %.1f m/s%s", compass(to + 180), compass(to), to, speed(level),
				fixed() ? " (fixed)" : "");
	}

	private static String compass(double degrees) {
		String[] names = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
		return names[(int) Math.floorMod(Math.round(degrees / 45.0), 8)];
	}
}
