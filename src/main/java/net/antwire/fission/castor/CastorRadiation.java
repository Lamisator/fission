package net.antwire.fission.castor;

import dev.radiation.api.RadiationApi;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * The radiation a CASTOR lets out while it travels. A cask is an entity, not a block, so it radiates as a Radiation
 * pulse: a radiating spot that follows it, renewed once a second and gone a few seconds after it is no longer renewed
 * (when the wagon is broken or unloaded, or its chunk unloads). Geiger counters, the rad meter and Atlas see it.
 */
public final class CastorRadiation {
	/** How long one renewal lasts; longer than the renewal interval so the spot never flickers. */
	private static final int TICKS = 60;

	private CastorRadiation() {
	}

	public static String key(net.minecraft.world.entity.Entity cask) {
		return "fission_castor_" + cask.getUUID();
	}

	/** Called at least once a second for every cask; {@code radsAtOneMetre} is what gets through its walls. */
	public static void radiate(ServerLevel level, String key, Vec3 centre, float radsAtOneMetre) {
		if (radsAtOneMetre <= 0.0005F) {
			RadiationApi.endPulse(key);
			return;
		}
		RadiationApi.pulse(level, key, centre, radsAtOneMetre, RadiationApi.radiusFor(radsAtOneMetre), TICKS);
	}

	public static void end(String key) {
		RadiationApi.endPulse(key);
	}
}
