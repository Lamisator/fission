package net.antwire.fission.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The shape of a reactor core and its neutron balance.
 * <p>
 * Neutrons are followed block by block: of those born in a fuel block, a few stay in its own channel and the rest
 * leave through its six faces. Graphite and beryllium scatter them on (and slow them down), the water in the channels
 * slows them down a little and absorbs some, control rods absorb them, vessel steel scatters them poorly and anything
 * else lets them leak away. Neutrons arriving back in a fuel block cause new fissions, slow ones far more readily than
 * fast ones. The ratio of one generation to the last, found by power iteration, is k-effective; the converged source
 * is the power distribution. Losing the water therefore makes a graphite core more reactive (positive void
 * coefficient, as in the RBMK) and a water-moderated one less (negative, as in light water reactors).
 */
public final class CoreModel {
	private static final int MAX_BLOCKS = 4096;
	private static final int STEPS = 14;
	private static final double STAY = 0.12;
	private static final double WATER_ABSORPTION = 0.09;
	private static final double DIRECT_THERMAL = 0.15;
	private static final double FAST_FISSION = 0.30;
	private static final double UTILISATION = 1.36;
	private static final double EPSILON = 1.05;
	private static final double ROD_ABSORPTION = 0.98;

	public final List<BlockPos> fuel = new ArrayList<>();
	public final List<BlockPos> rods = new ArrayList<>();
	public final List<BlockPos> reliefs = new ArrayList<>();
	public final List<BlockPos> inlets = new ArrayList<>();
	public final List<BlockPos> outlets = new ArrayList<>();
	public final Map<Long, CoreRole> roles = new HashMap<>();
	public final Map<Long, Integer> fuelIndex = new HashMap<>();
	public BlockPos center = BlockPos.ZERO;
	public int minX, minY, minZ, maxX, maxY, maxZ;

	// results of the last solve
	public double k;
	public double[] share = new double[0];
	public double peaking = 1;

	private double[][] thermal;
	private double[][] fast;

	public static CoreModel scan(Level level, BlockPos controller) {
		CoreModel m = new CoreModel();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (Direction d : Direction.values()) {
			BlockPos q = controller.relative(d);
			if (level.getBlockState(q).getBlock() instanceof CoreBlock && m.roles.putIfAbsent(q.asLong(), role(level, q)) == null) {
				queue.add(q);
			}
		}
		while (!queue.isEmpty() && m.roles.size() < MAX_BLOCKS) {
			BlockPos p = queue.poll();
			for (Direction d : Direction.values()) {
				BlockPos q = p.relative(d);
				if (!m.roles.containsKey(q.asLong()) && level.isLoaded(q) && level.getBlockState(q).getBlock() instanceof CoreBlock) {
					m.roles.put(q.asLong(), role(level, q));
					queue.add(q);
				}
			}
		}
		m.minX = m.minY = m.minZ = Integer.MAX_VALUE;
		m.maxX = m.maxY = m.maxZ = Integer.MIN_VALUE;
		long sx = 0, sy = 0, sz = 0;
		for (Map.Entry<Long, CoreRole> e : m.roles.entrySet()) {
			BlockPos p = BlockPos.of(e.getKey());
			switch (e.getValue()) {
				case FUEL -> {
					m.fuelIndex.put(e.getKey(), m.fuel.size());
					m.fuel.add(p);
				}
				case ROD -> m.rods.add(p);
				case RELIEF -> m.reliefs.add(p);
				case INLET -> m.inlets.add(p);
				case OUTLET -> m.outlets.add(p);
				default -> {
				}
			}
			m.minX = Math.min(m.minX, p.getX());
			m.minY = Math.min(m.minY, p.getY());
			m.minZ = Math.min(m.minZ, p.getZ());
			m.maxX = Math.max(m.maxX, p.getX());
			m.maxY = Math.max(m.maxY, p.getY());
			m.maxZ = Math.max(m.maxZ, p.getZ());
			sx += p.getX();
			sy += p.getY();
			sz += p.getZ();
		}
		if (!m.roles.isEmpty()) {
			int n = m.roles.size();
			BlockPos c = new BlockPos((int) Math.floorDiv(sx, n), (int) Math.floorDiv(sy, n), (int) Math.floorDiv(sz, n));
			// the emitter needs a core block to sit on: the one nearest the centre
			BlockPos best = BlockPos.of(m.roles.keySet().iterator().next());
			for (long l : m.roles.keySet()) {
				if (BlockPos.of(l).distSqr(c) < best.distSqr(c)) {
					best = BlockPos.of(l);
				}
			}
			m.center = best;
		}
		m.share = new double[m.fuel.size()];
		return m;
	}

	private static CoreRole role(Level level, BlockPos p) {
		return ((CoreBlock) level.getBlockState(p).getBlock()).role();
	}

	public boolean valid() {
		return !this.fuel.isEmpty();
	}

	public boolean same(CoreModel other) {
		return other != null && other.roles.equals(this.roles);
	}

	/** Transport results for one point of the rod/water grid. */
	private record Transport(double[][] thermal, double[][] fast) {
	}

	private final Map<Long, Transport> transports = new java.util.LinkedHashMap<>(16, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Transport> eldest) {
			return this.size() > 8;
		}
	};

	/**
	 * Solves the neutron balance. {@code rods} is the control rod insertion (0..1), {@code water} the fraction of the
	 * channels still filled with liquid water, {@code eta} and {@code poison} per fuel block (eta 0 for an empty channel).
	 * The neutron transport is computed on a grid of 1 % rod steps and 2 % water steps and k interpolated between the
	 * four surrounding grid points, so reactivity changes smoothly while the rods move.
	 */
	public void solve(double rods, double water, double[] eta, double[] poison) {
		int n = this.fuel.size();
		if (n == 0) {
			this.k = 0;
			return;
		}
		rods = Math.clamp(rods, 0, 1);
		water = Math.clamp(water, 0, 1);
		int r0 = (int) Math.min(99, Math.floor(rods * 100));
		int w0 = (int) Math.min(49, Math.floor(water * 50));
		double tr = rods * 100 - r0;
		double tw = water * 50 - w0;
		double k00 = this.iterate(this.transport(r0, w0, eta), eta, poison, w0 / 50.0, false);
		double k10 = this.iterate(this.transport(r0 + 1, w0, eta), eta, poison, w0 / 50.0, false);
		double k01 = this.iterate(this.transport(r0, w0 + 1, eta), eta, poison, (w0 + 1) / 50.0, false);
		double k11 = this.iterate(this.transport(r0 + 1, w0 + 1, eta), eta, poison, (w0 + 1) / 50.0, false);
		this.k = (k00 * (1 - tr) + k10 * tr) * (1 - tw) + (k01 * (1 - tr) + k11 * tr) * tw;
		// the power shape from the nearest grid point
		int rn = tr < 0.5 ? r0 : r0 + 1;
		int wn = tw < 0.5 ? w0 : w0 + 1;
		this.iterate(this.transport(rn, wn, eta), eta, poison, wn / 50.0, true);
	}

	private Transport transport(int rodStep, int waterStep, double[] eta) {
		long key = rodStep * 1000L + waterStep;
		Transport t = this.transports.get(key);
		if (t == null) {
			this.transport(rodStep / 100.0, waterStep / 50.0, eta);
			t = new Transport(this.thermal, this.fast);
			this.transports.put(key, t);
		}
		return t;
	}

	/** Power iteration; returns k, and with {@code keep} stores the converged power shape. */
	private double iterate(Transport t, double[] eta, double[] poison, double water, boolean keep) {
		int n = this.fuel.size();
		double base = UTILISATION * EPSILON * (1 - WATER_ABSORPTION * water);
		double[] s = new double[n];
		java.util.Arrays.fill(s, 1.0 / n);
		double k = 0;
		for (int it = 0; it < 40; it++) {
			double[] next = new double[n];
			for (int i = 0; i < n; i++) {
				if (s[i] == 0) {
					continue;
				}
				double[] th = t.thermal[i];
				double[] fa = t.fast[i];
				for (int j = 0; j < n; j++) {
					if (eta[j] > 0) {
						next[j] += s[i] * eta[j] * (1 - poison[j]) * base * (th[j] + FAST_FISSION * fa[j]);
					}
				}
			}
			double sum = 0;
			for (double v : next) {
				sum += v;
			}
			k = sum;
			if (sum <= 0) {
				break;
			}
			for (int j = 0; j < n; j++) {
				s[j] = next[j] / sum;
			}
		}
		if (keep) {
			this.share = s;
			double max = 0;
			for (double v : s) {
				max = Math.max(max, v);
			}
			this.peaking = max * n;
		}
		return k;
	}

	/** Where the neutrons born in each fuel block end up, slow and fast, for this rod position and water level. */
	private void transport(double rods, double water, double[] eta) {
		int n = this.fuel.size();
		this.thermal = new double[n][n];
		this.fast = new double[n][n];
		double ownThermal = 0.10 + DIRECT_THERMAL * water;
		for (int i = 0; i < n; i++) {
			BlockPos p = this.fuel.get(i);
			double[] th = this.thermal[i];
			double[] fa = this.fast[i];
			th[i] += STAY * ownThermal;
			fa[i] += STAY * (1 - ownThermal);
			double face = (1 - STAY) / 6;
			Map<Long, double[]> front = new HashMap<>();
			for (Direction d : Direction.values()) {
				front.computeIfAbsent(p.relative(d).asLong(), k -> new double[2])[1] += face;
			}
			for (int step = 0; step < STEPS && !front.isEmpty(); step++) {
				Map<Long, double[]> next = new HashMap<>();
				for (Map.Entry<Long, double[]> e : front.entrySet()) {
					long q = e.getKey();
					double mt = e.getValue()[0];
					double mf = e.getValue()[1];
					CoreRole r = this.roles.get(q);
					if (r == null) {
						continue;
					}
					double ret;
					boolean mod;
					if (r == CoreRole.FUEL) {
						int j = this.fuelIndex.get(q);
						if (eta[j] > 0) {
							th[j] += mt + mf * DIRECT_THERMAL * water;
							fa[j] += mf * (1 - DIRECT_THERMAL * water);
							continue;
						}
						// an empty channel: just water
						ret = 0.2 + 0.7 * water;
						mod = water > 0.3;
					} else if (r == CoreRole.ROD) {
						ret = (1 - ROD_ABSORPTION * rods) * (0.9 - WATER_ABSORPTION * water);
						mod = water > 0.3;
					} else {
						ret = r.retention();
						mod = r.moderates();
					}
					if (mod) {
						mt += mf;
						mf = 0;
					}
					double t = mt * ret / 6;
					double f = mf * ret / 6;
					if (t + f < 1e-7) {
						continue;
					}
					BlockPos qp = BlockPos.of(q);
					for (Direction d : Direction.values()) {
						double[] a = next.computeIfAbsent(qp.relative(d).asLong(), x -> new double[2]);
						a[0] += t;
						a[1] += f;
					}
				}
				front = next;
			}
		}
	}

	/** Forget the cached transport (fuel was added or removed). */
	public void invalidate() {
		this.transports.clear();
	}
}
