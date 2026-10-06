package net.antwire.fission.world;

import net.antwire.fission.block.CondenserBlock;
import net.antwire.fission.block.PipeBlock;
import net.antwire.fission.block.entity.FeedwaterPumpBlockEntity;
import net.antwire.fission.block.entity.ReactorControllerBlockEntity;
import net.antwire.fission.block.entity.SteamTurbineBlockEntity;
import net.antwire.fission.reactor.CoreModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Water and steam for one reactor, twice a second. Feedwater pumps on the inlets' pipes keep the core topped up.
 * Turbines on the outlets' pipes take what their generators need once there are 40 bar; above 70 bar the turbine bypass
 * dumps the rest to the condensers, and above 85 bar the relief valves blow.
 */
public final class FluidNetwork {
	/** kg/s a pipe network carries at most. */
	public static final double PIPE_CAPACITY = 40;
	private static final double SOLVE_SECONDS = 0.5;

	private FluidNetwork() {
	}

	public static void solve(ServerLevel level, ReactorControllerBlockEntity r) {
		CoreModel core = r.core;
		if (core == null) {
			return;
		}
		// feedwater
		List<FeedwaterPumpBlockEntity> pumps = new ArrayList<>();
		for (BlockPos p : endpoints(level, core.inlets, PipeMedium.WATER)) {
			if (level.getBlockEntity(p) instanceof FeedwaterPumpBlockEntity pump) {
				pumps.add(pump);
			}
		}
		double want = Math.max(0, r.capacity() - r.water) / SOLVE_SECONDS + r.steamRate;
		double avail = 0;
		for (FeedwaterPumpBlockEntity p : pumps) {
			avail += p.available();
		}
		double take = Math.min(Math.min(want, avail), PIPE_CAPACITY);
		for (FeedwaterPumpBlockEntity p : pumps) {
			p.deliver(avail > 0 ? take * p.available() / avail : 0, r.pressure);
		}
		r.feedRate = take;
		// steam
		List<SteamTurbineBlockEntity> turbines = new ArrayList<>();
		double condensers = 0;
		for (BlockPos p : endpoints(level, core.outlets, PipeMedium.STEAM)) {
			if (level.getBlockEntity(p) instanceof SteamTurbineBlockEntity t) {
				turbines.add(t);
			} else if (level.getBlockState(p).getBlock() instanceof CondenserBlock) {
				condensers += CondenserBlock.capacity(level, p);
			}
		}
		double offer = r.pressure > 1.5 ? Math.max(0, r.steam - steamAt(r, 1.5)) / SOLVE_SECONDS : 0;
		double demand = 0;
		for (SteamTurbineBlockEntity t : turbines) {
			demand += t.steamDemand(r.pressure);
		}
		double toTurbines = Math.min(Math.min(demand, offer), PIPE_CAPACITY);
		for (SteamTurbineBlockEntity t : turbines) {
			t.receive(demand > 0 ? toTurbines * t.steamDemand(r.pressure) / demand : 0, r.pressure);
		}
		double dump = r.pressure > 70 ? Math.max(0, r.steam - steamAt(r, 70)) / SOLVE_SECONDS : 0;
		double toCondensers = Math.max(0, Math.min(Math.min(condensers, dump), Math.min(offer - toTurbines, PIPE_CAPACITY - toTurbines)));
		r.steamOutRate = toTurbines + toCondensers;
	}

	private static double steamAt(ReactorControllerBlockEntity r, double bar) {
		return (bar - 1) / 69 * r.channels() * 20;
	}

	/** Non-pipe blocks reachable from the start blocks through pipes of this medium (and directly next to them). */
	public static Set<BlockPos> endpoints(ServerLevel level, List<BlockPos> starts, PipeMedium medium) {
		Set<BlockPos> out = new HashSet<>();
		Set<Long> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (BlockPos s : starts) {
			seen.add(s.asLong());
		}
		for (BlockPos s : starts) {
			for (Direction d : Direction.values()) {
				BlockPos q = s.relative(d);
				if (!seen.add(q.asLong()) || !level.isLoaded(q)) {
					continue;
				}
				if (level.getBlockState(q).getBlock() instanceof PipeBlock pb && pb.medium() == medium) {
					queue.add(q);
				} else {
					out.add(q);
				}
			}
		}
		while (!queue.isEmpty() && seen.size() < 2048) {
			BlockPos p = queue.poll();
			for (Direction d : Direction.values()) {
				BlockPos q = p.relative(d);
				if (!seen.add(q.asLong()) || !level.isLoaded(q)) {
					continue;
				}
				if (level.getBlockState(q).getBlock() instanceof PipeBlock pb && pb.medium() == medium) {
					queue.add(q);
				} else {
					out.add(q);
				}
			}
		}
		return out;
	}
}
