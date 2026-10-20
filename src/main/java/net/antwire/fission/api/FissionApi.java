package net.antwire.fission.api;

import java.util.ArrayList;
import java.util.List;
import net.antwire.fission.block.entity.FuelChannelBlockEntity;
import net.antwire.fission.block.entity.ReactorControllerBlockEntity;
import net.antwire.fission.reactor.CoreModel;
import net.antwire.fission.world.ReactorExplosion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * For other mods: where the loaded reactors are, and a way to destroy one from outside - a nuclear blast that reaches a
 * reactor tears its core open, and whatever fuel is in it goes up with it.
 */
public final class FissionApi {
	private FissionApi() {
	}

	/**
	 * A loaded reactor.
	 *
	 * @param controller where its controller stands (the handle for {@link #destroy})
	 * @param coreMin the lower corner of the box around its core
	 * @param coreMax the upper corner of that box
	 * @param fuelled channels with a fuel rod in them
	 * @param thermalWatts thermal power, decay heat included
	 */
	public record Reactor(BlockPos controller, BlockPos coreMin, BlockPos coreMax, int fuelled, double thermalWatts) {
	}

	/** The loaded reactors whose core comes within {@code radius} blocks (horizontally) of {@code center}. */
	public static List<Reactor> reactorsNear(ServerLevel level, BlockPos center, double radius) {
		List<Reactor> out = new ArrayList<>();
		for (ReactorControllerBlockEntity r : ReactorControllerBlockEntity.loaded(level)) {
			CoreModel core = r.core;
			if (core == null || !core.valid()) {
				continue;
			}
			double dx = Math.max(0, Math.max(core.minX - center.getX(), center.getX() - core.maxX));
			double dz = Math.max(0, Math.max(core.minZ - center.getZ(), center.getZ() - core.maxZ));
			if (dx * dx + dz * dz > radius * radius) {
				continue;
			}
			int fuelled = 0;
			for (BlockPos p : core.fuel) {
				if (level.getBlockEntity(p) instanceof FuelChannelBlockEntity ch && !ch.rod().isEmpty()) {
					fuelled++;
				}
			}
			out.add(new Reactor(r.getBlockPos(), new BlockPos(core.minX, core.minY, core.minZ), new BlockPos(core.maxX, core.maxY, core.maxZ),
				fuelled, r.thermalPower()));
		}
		return out;
	}

	/**
	 * Destroys a reactor by a blast from outside: the containment counts for nothing, the core blows apart in a string of
	 * explosions, fuel and burning graphite are thrown far out and the whole inventory is released. A reactor without fuel
	 * is only wrecked.
	 *
	 * @param violence 0 at the edge of what destroys the reactor, 1 for a direct hit
	 * @return false if there is no intact reactor at {@code controller}
	 */
	public static boolean destroy(ServerLevel level, BlockPos controller, double violence, String why) {
		if (!(level.getBlockEntity(controller) instanceof ReactorControllerBlockEntity r) || r.destroyed || r.core == null || !r.core.valid()) {
			return false;
		}
		ReactorExplosion.explode(level, r, ReactorExplosion.EXTERNAL_MIN + (ReactorExplosion.EXTERNAL_MAX - ReactorExplosion.EXTERNAL_MIN)
			* Math.clamp(violence, 0.0, 1.0), why, true);
		return true;
	}
}
