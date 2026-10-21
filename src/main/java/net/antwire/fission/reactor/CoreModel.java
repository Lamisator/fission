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
	private static final int MAX_BLOCKS = 32768;
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
	/** Differential rod worth at the last solve: reactivity per full stroke at the present rod position (positive). */
	public double rodWorth = 0.2;

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

	// ------------------------------------------------------------------ the core on a grid

	/** The core on a box grid with a one-block margin: role ordinal per cell (-1 = not core) and fuel index per cell (-1). */
	private byte[] grid;
	private int[] fuelCell;
	private int[] fuelAt;
	private int dx, dxz;

	private void buildGrid() {
		if (this.grid != null) {
			return;
		}
		int sx = this.maxX - this.minX + 3, sz = this.maxZ - this.minZ + 3, sy = this.maxY - this.minY + 3;
		this.dx = sx;
		this.dxz = sx * sz;
		this.grid = new byte[sx * sz * sy];
		this.fuelAt = new int[this.grid.length];
		java.util.Arrays.fill(this.grid, (byte) -1);
		java.util.Arrays.fill(this.fuelAt, -1);
		for (Map.Entry<Long, CoreRole> e : this.roles.entrySet()) {
			this.grid[this.cell(BlockPos.of(e.getKey()))] = (byte) e.getValue().ordinal();
		}
		this.fuelCell = new int[this.fuel.size()];
		for (int i = 0; i < this.fuel.size(); i++) {
			this.fuelCell[i] = this.cell(this.fuel.get(i));
			this.fuelAt[this.fuelCell[i]] = i;
		}
	}

	private int cell(BlockPos p) {
		return (p.getY() - this.minY + 1) * this.dxz + (p.getZ() - this.minZ + 1) * this.dx + (p.getX() - this.minX + 1);
	}

	/**
	 * Transport results for one point of the rod/water grid, sparse: for fuel block i, the fuel blocks {@code to[i]} its
	 * neutrons reach and the fissions they cause there per unit eta ({@code w[i]}, slow plus fast). The k and power shape
	 * last worked out with it are kept for as long as the fuel stays the same, and the shape is where the next power
	 * iteration starts.
	 */
	private static final class Transport {
		final int[][] to;
		final double[][] w;
		double[] etaSeen;
		double[] poisonSeen;
		double k;
		double[] shape;

		Transport(int[][] to, double[][] w) {
			this.to = to;
			this.w = w;
		}
	}

	private final Map<Long, Transport> transports = new java.util.LinkedHashMap<>(16, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Transport> eldest) {
			return this.size() > (CoreModel.this.background() ? 24 : 8);
		}
	};

	// ------------------------------------------------------------------ solving, here or in the background

	/** Cores with at least this many fuel blocks are solved on a background thread. */
	private static final int BACKGROUND_FUEL = 300;
	private static final java.util.concurrent.ExecutorService SOLVER = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Fission core solver");
		t.setDaemon(true);
		t.setPriority(Thread.NORM_PRIORITY - 1);
		return t;
	});
	private static final java.util.concurrent.ForkJoinPool ROWS = new java.util.concurrent.ForkJoinPool(
			Math.clamp(Runtime.getRuntime().availableProcessors() - 1, 1, 4));

	/** The result of one solve: k, its slope over the rod stroke, the rod worth and the power shape. */
	private record Result(double rods, double water, double k, double dkdRods, double rodWorth, double[] share, double peaking) {
	}

	private record Request(double rods, double water, double[] eta, double[] poison, int generation) {
	}

	private volatile Request pending;
	private volatile Result latest;
	private volatile int generation;
	private final java.util.concurrent.atomic.AtomicBoolean scheduled = new java.util.concurrent.atomic.AtomicBoolean();
	private int workGeneration;
	private volatile Request lastTaken;

	private boolean background() {
		return this.fuel.size() >= BACKGROUND_FUEL;
	}

	/** False while a big core's first solve is still running in the background: k, the shape and the rod worth are not known yet. */
	public boolean ready() {
		return !this.background() || this.applied;
	}

	private boolean applied;

	/**
	 * Solves the neutron balance. {@code rods} is the control rod insertion (0..1), {@code water} the fraction of the
	 * channels still filled with liquid water, {@code eta} and {@code poison} per fuel block (eta 0 for an empty channel).
	 * The neutron transport is computed on a grid of 1 % rod steps and 2 % water steps and k interpolated between the
	 * four surrounding grid points, so reactivity changes smoothly while the rods move. Each grid point remembers its k
	 * until the fuel changes (the controller hands in new arrays when it does).
	 * <p>
	 * A big core (hundreds of channels) is solved on a background thread, which also works out the grid points next to
	 * the present one (and the rods fully in) before the rods get there. Until a new result comes, k follows the rods
	 * along the slope of the last one.
	 */
	public void solve(double rods, double water, double[] eta, double[] poison) {
		if (this.fuel.isEmpty()) {
			this.k = 0;
			return;
		}
		rods = Math.clamp(rods, 0, 1);
		water = Math.clamp(water, 0, 1);
		if (!this.background()) {
			this.apply(this.compute(rods, water, eta, poison), rods);
			return;
		}
		this.pending = new Request(rods, water, eta, poison, this.generation);
		if (this.scheduled.compareAndSet(false, true)) {
			SOLVER.execute(this::work);
		}
		Result r = this.latest;
		if (r != null) {
			this.apply(r, rods);
		}
	}

	private void apply(Result r, double rods) {
		this.k = Math.max(0, r.k + r.dkdRods * (rods - r.rods));
		this.rodWorth = r.rodWorth;
		this.share = r.share;
		this.peaking = r.peaking;
		this.applied = true;
	}

	/** Background: solve the newest request, then look ahead while nothing newer is waiting. */
	private void work() {
		try {
			while (true) {
				Request q = this.pending;
				if (q == null || q == this.lastTaken) {
					break;
				}
				this.lastTaken = q;
				if (q.generation != this.workGeneration) {
					this.transports.clear();
					this.workGeneration = q.generation;
				}
				this.latest = this.compute(q.rods, q.water, q.eta, q.poison);
				int r0 = (int) Math.min(99, Math.floor(q.rods * 100));
				int w0 = (int) Math.min(49, Math.floor(q.water * 50));
				int[][] ahead = {{100, w0}, {100, w0 + 1}, {r0 - 1, w0}, {r0 + 2, w0}, {r0 - 1, w0 + 1}, {r0 + 2, w0 + 1}};
				for (int[] g : ahead) {
					if (this.pending != q || q.generation != this.generation) {
						break;
					}
					if (g[0] >= 0 && g[0] <= 100 && g[1] <= 50) {
						this.transport(g[0], g[1], q.eta);
					}
				}
			}
		} catch (Throwable t) {
			net.antwire.fission.Fission.LOGGER.error("Core solver failed", t);
		} finally {
			this.scheduled.set(false);
			// a request that came in after the loop last looked
			Request q = this.pending;
			if (q != null && q != this.lastTaken && this.scheduled.compareAndSet(false, true)) {
				SOLVER.execute(this::work);
			}
		}
	}

	private Result compute(double rods, double water, double[] eta, double[] poison) {
		int n = this.fuel.size();
		int r0 = (int) Math.min(99, Math.floor(rods * 100));
		int w0 = (int) Math.min(49, Math.floor(water * 50));
		double tr = rods * 100 - r0;
		double tw = water * 50 - w0;
		double k00 = this.kAt(r0, w0, eta, poison);
		double k10 = this.kAt(r0 + 1, w0, eta, poison);
		double k01 = this.kAt(r0, w0 + 1, eta, poison);
		double k11 = this.kAt(r0 + 1, w0 + 1, eta, poison);
		double k = (k00 * (1 - tr) + k10 * tr) * (1 - tw) + (k01 * (1 - tr) + k11 * tr) * tw;
		double dk = ((k00 - k10) * (1 - tw) + (k01 - k11) * tw) * 100;
		double worth = k > 0 ? dk / (k * k) : 0.2;
		// the power shape from the nearest grid point
		int rn = tr < 0.5 ? r0 : r0 + 1;
		int wn = tw < 0.5 ? w0 : w0 + 1;
		this.kAt(rn, wn, eta, poison);
		double[] s = this.transport(rn, wn, eta).shape;
		double max = 0;
		for (double v : s) {
			max = Math.max(max, v);
		}
		return new Result(rods, water, k, -dk, worth, s, max * n);
	}

	private double kAt(int rodStep, int waterStep, double[] eta, double[] poison) {
		Transport t = this.transport(rodStep, waterStep, eta);
		if (t.etaSeen != eta || t.poisonSeen != poison) {
			this.iterate(t, eta, poison, waterStep / 50.0);
			t.etaSeen = eta;
			t.poisonSeen = poison;
		}
		return t.k;
	}

	private Transport transport(int rodStep, int waterStep, double[] eta) {
		long key = rodStep * 1000L + waterStep;
		Transport t = this.transports.get(key);
		if (t == null) {
			t = this.computeTransport(rodStep / 100.0, waterStep / 50.0, eta);
			this.transports.put(key, t);
		}
		return t;
	}

	/** Power iteration, started from the last shape this core had; stores k and the converged power shape in {@code t}. */
	private void iterate(Transport t, double[] eta, double[] poison, double water) {
		int n = this.fuel.size();
		double base = UTILISATION * EPSILON * (1 - WATER_ABSORPTION * water);
		double[] f = new double[n];
		for (int j = 0; j < n; j++) {
			f[j] = eta[j] > 0 ? eta[j] * (1 - poison[j]) * base : 0;
		}
		double[] s = new double[n];
		double[] start = t.shape != null && t.shape.length == n ? t.shape : this.share.length == n ? this.share : null;
		double sum0 = 0;
		if (start != null) {
			for (int i = 0; i < n; i++) {
				s[i] = f[i] > 0 ? start[i] : 0;
				sum0 += s[i];
			}
		}
		if (sum0 <= 0) {
			java.util.Arrays.fill(s, 1.0 / n);
		} else {
			for (int i = 0; i < n; i++) {
				s[i] /= sum0;
			}
		}
		boolean warm = sum0 > 0;
		double k = 0;
		double[] next = new double[n];
		int calm = 0;
		for (int it = 0; it < (warm ? 120 : 400); it++) {
			java.util.Arrays.fill(next, 0);
			for (int i = 0; i < n; i++) {
				double si = s[i];
				if (si == 0) {
					continue;
				}
				int[] to = t.to[i];
				double[] w = t.w[i];
				for (int m = 0; m < to.length; m++) {
					next[to[m]] += si * w[m];
				}
			}
			double sum = 0;
			for (int j = 0; j < n; j++) {
				next[j] *= f[j];
				sum += next[j];
			}
			double change = Math.abs(sum - k);
			k = sum;
			if (sum <= 0) {
				break;
			}
			double moved = 0;
			for (int j = 0; j < n; j++) {
				double v = next[j] / sum;
				moved = Math.max(moved, Math.abs(v - s[j]) * n);
				s[j] = v;
			}
			// converged: k steady to 0.1 pcm and no block's share moving by more than 0.01 % of the average
			calm = change < 1e-6 * k && moved < 1e-4 ? calm + 1 : 0;
			if (it >= 3 && calm >= 2) {
				break;
			}
		}
		t.k = k;
		t.shape = s;
	}

	/** Where the neutrons born in each fuel block end up, slow and fast, for this rod position and water level. */
	private Transport computeTransport(double rods, double water, double[] eta) {
		this.buildGrid();
		int n = this.fuel.size();
		int[][] to = new int[n][];
		double[][] w = new double[n][];
		if (this.background()) {
			int chunks = ROWS.getParallelism() * 4;
			ROWS.submit(() -> java.util.stream.IntStream.range(0, chunks).parallel()
					.forEach(c -> this.rows(rods, water, eta, to, w, (int) ((long) n * c / chunks), (int) ((long) n * (c + 1) / chunks)))).join();
		} else {
			this.rows(rods, water, eta, to, w, 0, n);
		}
		return new Transport(to, w);
	}

	/** Transport rows {@code from} until {@code until}, with scratch arrays of its own (so chunks can run in parallel). */
	private void rows(double rods, double water, double[] eta, int[][] to, double[][] w, int from, int until) {
		int n = this.fuel.size();
		int size = this.grid.length;
		int[] off = {-this.dxz, this.dxz, -this.dx, this.dx, -1, 1};
		// scratch: two fronts (slow and fast per cell), the cells each one holds, and the row being built
		double[] at = new double[size], af = new double[size], bt = new double[size], bf = new double[size];
		int[] aCells = new int[size], bCells = new int[size];
		boolean[] aIn = new boolean[size], bIn = new boolean[size];
		double[] rowT = new double[n], rowF = new double[n];
		int[] rowCells = new int[n];
		boolean[] rowIn = new boolean[n];
		double ownThermal = 0.10 + DIRECT_THERMAL * water;
		double rodRet = (1 - ROD_ABSORPTION * rods) * (0.9 - WATER_ABSORPTION * water);
		double emptyRet = 0.2 + 0.7 * water;
		boolean wetMod = water > 0.3;
		CoreRole[] roleOf = CoreRole.values();
		for (int i = from; i < until; i++) {
			int rowN = 0;
			rowT[i] += STAY * ownThermal;
			rowF[i] += STAY * (1 - ownThermal);
			rowIn[i] = true;
			rowCells[rowN++] = i;
			double face = (1 - STAY) / 6;
			int an = 0;
			int c0 = this.fuelCell[i];
			for (int o : off) {
				int q = c0 + o;
				if (!aIn[q]) {
					aIn[q] = true;
					aCells[an++] = q;
				}
				af[q] += face;
			}
			for (int step = 0; step < STEPS && an > 0; step++) {
				int bn = 0;
				for (int a = 0; a < an; a++) {
					int q = aCells[a];
					double mt = at[q], mf = af[q];
					at[q] = 0;
					af[q] = 0;
					aIn[q] = false;
					int role = this.grid[q];
					if (role < 0) {
						continue;
					}
					CoreRole r = roleOf[role];
					double ret;
					boolean mod;
					if (r == CoreRole.FUEL) {
						int j = this.fuelAt[q];
						if (eta[j] > 0) {
							if (!rowIn[j]) {
								rowIn[j] = true;
								rowCells[rowN++] = j;
							}
							rowT[j] += mt + mf * DIRECT_THERMAL * water;
							rowF[j] += mf * (1 - DIRECT_THERMAL * water);
							continue;
						}
						// an empty channel: just water
						ret = emptyRet;
						mod = wetMod;
					} else if (r == CoreRole.ROD) {
						ret = rodRet;
						mod = wetMod;
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
					for (int o : off) {
						int nq = q + o;
						if (!bIn[nq]) {
							bIn[nq] = true;
							bCells[bn++] = nq;
						}
						bt[nq] += t;
						bf[nq] += f;
					}
				}
				// swap the fronts
				double[] d = at; at = bt; bt = d;
				d = af; af = bf; bf = d;
				int[] c = aCells; aCells = bCells; bCells = c;
				boolean[] b = aIn; aIn = bIn; bIn = b;
				an = bn;
			}
			for (int a = 0; a < an; a++) {
				int q = aCells[a];
				at[q] = 0;
				af[q] = 0;
				aIn[q] = false;
			}
			int[] ti = new int[rowN];
			double[] wi = new double[rowN];
			for (int m = 0; m < rowN; m++) {
				int j = rowCells[m];
				ti[m] = j;
				wi[m] = rowT[j] + FAST_FISSION * rowF[j];
				rowT[j] = 0;
				rowF[j] = 0;
				rowIn[j] = false;
			}
			to[i] = ti;
			w[i] = wi;
		}
	}

	/** Forget the cached transport (fuel was added or removed). */
	public void invalidate() {
		if (this.background()) {
			// the solver thread owns the cache; it starts afresh with the next request
			this.generation++;
			return;
		}
		this.transports.clear();
	}
}
