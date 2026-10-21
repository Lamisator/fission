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
		// feedwater: each pipe network (on one inlet or several) carries up to 40 kg/s
		List<List<FeedwaterPumpBlockEntity>> feedNets = new ArrayList<>();
		for (Set<BlockPos> net : networks(level, core.inlets, PipeMedium.WATER)) {
			List<FeedwaterPumpBlockEntity> pumps = new ArrayList<>();
			for (BlockPos p : net) {
				if (level.getBlockEntity(p) instanceof FeedwaterPumpBlockEntity pump) {
					pumps.add(pump);
				}
			}
			feedNets.add(pumps);
		}
		double want = Math.max(0, r.capacity() - r.water) / SOLVE_SECONDS + r.steamRate;
		double[] avail = new double[feedNets.size()];
		double availAll = 0;
		for (int i = 0; i < avail.length; i++) {
			for (FeedwaterPumpBlockEntity p : feedNets.get(i)) {
				avail[i] += p.available();
			}
			avail[i] = Math.min(avail[i], PIPE_CAPACITY);
			availAll += avail[i];
		}
		double take = Math.min(want, availAll);
		for (int i = 0; i < avail.length; i++) {
			double net = availAll > 0 ? take * avail[i] / availAll : 0;
			double raw = 0;
			for (FeedwaterPumpBlockEntity p : feedNets.get(i)) {
				raw += p.available();
			}
			for (FeedwaterPumpBlockEntity p : feedNets.get(i)) {
				p.deliver(raw > 0 ? net * p.available() / raw : 0, r.pressure);
			}
		}
		r.feedRate = take;
		// steam: turbines first, then the condensers above 70 bar, 40 kg/s per pipe network
		List<List<SteamTurbineBlockEntity>> turbineNets = new ArrayList<>();
		List<Double> condenserNets = new ArrayList<>();
		for (Set<BlockPos> net : networks(level, core.outlets, PipeMedium.STEAM)) {
			List<SteamTurbineBlockEntity> turbines = new ArrayList<>();
			double condensers = 0;
			for (BlockPos p : net) {
				if (level.getBlockEntity(p) instanceof SteamTurbineBlockEntity t) {
					turbines.add(t);
				} else if (level.getBlockState(p).getBlock() instanceof CondenserBlock) {
					condensers += CondenserBlock.capacity(level, p);
				}
			}
			turbineNets.add(turbines);
			condenserNets.add(condensers);
		}
		double offer = r.pressure > 1.5 ? Math.max(0, r.steam - steamAt(r, 1.5)) / SOLVE_SECONDS : 0;
		double dump = r.pressure > 70 ? Math.max(0, r.steam - steamAt(r, 70)) / SOLVE_SECONDS : 0;
		int nets = turbineNets.size();
		double[] demand = new double[nets];
		double demandAll = 0;
		for (int i = 0; i < nets; i++) {
			for (SteamTurbineBlockEntity t : turbineNets.get(i)) {
				demand[i] += t.steamDemand(r.pressure);
			}
			demand[i] = Math.min(demand[i], PIPE_CAPACITY);
			demandAll += demand[i];
		}
		double toTurbines = Math.min(demandAll, offer);
		for (int i = 0; i < nets; i++) {
			double net = demandAll > 0 ? toTurbines * demand[i] / demandAll : 0;
			double raw = 0;
			for (SteamTurbineBlockEntity t : turbineNets.get(i)) {
				raw += t.steamDemand(r.pressure);
			}
			for (SteamTurbineBlockEntity t : turbineNets.get(i)) {
				t.receive(raw > 0 ? net * t.steamDemand(r.pressure) / raw : 0, r.pressure);
			}
		}
		// the bypass: what each network's pipe has left after its turbines, up to its condensers
		double room = 0;
		for (int i = 0; i < nets; i++) {
			double used = demandAll > 0 ? toTurbines * demand[i] / demandAll : 0;
			room += Math.min(condenserNets.get(i), PIPE_CAPACITY - used);
		}
		double toCondensers = Math.max(0, Math.min(Math.min(room, dump), offer - toTurbines));
		r.steamOutRate = toTurbines + toCondensers;
	}

	/** The pipe networks of this medium on the start blocks, each as the non-pipe blocks it reaches (networks shared by several starts count once). */
	public static List<Set<BlockPos>> networks(ServerLevel level, List<BlockPos> starts, PipeMedium medium) {
		List<Set<BlockPos>> out = new ArrayList<>();
		Set<Long> pipes = new HashSet<>();
		Set<BlockPos> claimed = new HashSet<>();
		for (BlockPos s : starts) {
			Set<BlockPos> ends = new HashSet<>();
			ArrayDeque<BlockPos> queue = new ArrayDeque<>();
			for (Direction d : Direction.values()) {
				BlockPos q = s.relative(d);
				if (level.isLoaded(q) && level.getBlockState(q).getBlock() instanceof PipeBlock pb && pb.medium() == medium) {
					if (pipes.add(q.asLong())) {
						queue.add(q);
					}
				} else if (!starts.contains(q)) {
					ends.add(q);
				}
			}
			int seen = 0;
			while (!queue.isEmpty() && seen++ < 2048) {
				BlockPos p = queue.poll();
				for (Direction d : Direction.values()) {
					BlockPos q = p.relative(d);
					if (!level.isLoaded(q) || starts.contains(q)) {
						continue;
					}
					if (level.getBlockState(q).getBlock() instanceof PipeBlock pb && pb.medium() == medium) {
						if (pipes.add(q.asLong())) {
							queue.add(q);
						}
					} else {
						ends.add(q);
					}
				}
			}
			// a machine reached from two starts belongs to the first one's network
			ends.removeIf(e -> !claimed.add(e));
			if (!ends.isEmpty()) {
				out.add(ends);
			}
		}
		return out;
	}

	private static double steamAt(ReactorControllerBlockEntity r, double bar) {
		return (bar - 1) / 69 * r.channels() * 20;
	}
}
