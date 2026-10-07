package net.antwire.fission.block.entity;

import dev.radiation.api.RadiationApi;
import net.antwire.fission.Fission;
import net.antwire.fission.block.ControlRodBlock;
import net.antwire.fission.item.FuelRodItem;
import net.antwire.fission.network.ReactorNetwork;
import net.antwire.fission.nuclear.FuelData;
import net.antwire.fission.nuclear.FuelType;
import net.antwire.fission.nuclear.Nuclear;
import net.antwire.fission.reactor.Alarm;
import net.antwire.fission.reactor.CoreModel;
import net.antwire.fission.registry.ModBlockEntities;
import net.antwire.fission.registry.ModComponents;
import net.antwire.fission.registry.ModSounds;
import net.antwire.fission.world.Corium;
import net.antwire.fission.world.FluidNetwork;
import net.antwire.fission.world.ReactorExplosion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The reactor: finds its core, and every tick (in real time) runs the point kinetics with one group of delayed
 * neutrons (prompt jump), the fuel and water temperatures, boiling, the steam pressure, the reactor protection system
 * and the automatic power control. Slow things (decay heat, iodine and xenon, burnup and fission products) run on
 * game time, 72 game seconds per real second.
 */
public class ReactorControllerBlockEntity extends BlockEntity {
	public static final double BETA = 0.0065;
	private static final double LAMBDA = 0.08;
	private static final double GENERATION_TIME = 1.0e-3;
	private static final double DOPPLER = 2.5e-5;
	public static final double WATER_PER_CHANNEL = 100;
	private static final double STEAM_REF_PER_CHANNEL = 20;
	private static final double LATENT_HEAT = 2.0e6;
	private static final double DT = 0.05;
	private static final double[] DECAY_FRACTION = {0.030, 0.025, 0.012};
	private static final double[] DECAY_TAU = {50, 2000, 1.0e5};
	private static final double LAMBDA_I = Math.log(2) / (6.57 * 3600);
	private static final double LAMBDA_XE = Math.log(2) / (9.14 * 3600);
	private static final double XE_EQ_FULL = (0.064 + 0.0025) / (4 * LAMBDA_XE);
	private static final double XENON_WORTH = 0.028;
	public static final double MELT_TEMP = 2800;

	public @Nullable CoreModel core;
	private long nextScan;
	private double precursor;
	public double power;
	public double rho;
	public double period = Double.POSITIVE_INFINITY;
	public double rods = 1;
	public double rodTarget = 1;
	public boolean scram;
	public String trip = "";
	public boolean auto;
	public double setpoint = 1.0e6;
	public boolean rps = true;
	public double fuelTemp = 20;
	public double waterTemp = 20;
	public double water;
	public double steam;
	public double pressure = 1;
	public double steamRate;
	public double feedRate;
	public double steamOutRate;
	public double ventRate;
	public double xenonWorth;
	public double hotTemp = 20;
	public double voidFraction;
	private final double[] decay = new double[3];
	private double iodine;
	private double xenon;
	private double[] eta = new double[0];
	private double[] poison = new double[0];
	private double[] energy = new double[0];
	private boolean fuelDirty = true;
	private double solvedRods = -1;
	private double solvedWater = -1;
	private long solvedAt;
	public int alarms;
	private int lastAlarms;
	public boolean destroyed;
	public final float[] history = new float[240];
	public int historyPos;
	private double rodDose;
	private int meltedChannels;
	// client copy for the in-world displays
	public int mapW;
	public int mapH;
	public byte[] map = new byte[0];

	public ReactorControllerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.REACTOR_CONTROLLER, pos, state);
	}

	public int channels() {
		return this.core == null ? 0 : this.core.fuel.size();
	}

	public double nominal() {
		return Math.max(1, this.channels()) * Nuclear.CHANNEL_WATTS;
	}

	public double thermalPower() {
		return this.power + this.decay[0] + this.decay[1] + this.decay[2];
	}

	public double decayHeat() {
		return this.decay[0] + this.decay[1] + this.decay[2];
	}

	public double capacity() {
		return Math.max(1, this.channels()) * WATER_PER_CHANNEL;
	}

	public double k() {
		return this.core == null ? 0 : this.core.k;
	}

	private double steamAt(double bar) {
		return (bar - 1) / 69 * this.channels() * STEAM_REF_PER_CHANNEL;
	}

	// ------------------------------------------------------------------ commands

	public void scram(String why) {
		if (!this.scram) {
			this.scram = true;
			this.trip = why;
			this.auto = false;
			if (this.level != null) {
				this.level.playSound(null, this.worldPosition, ModSounds.SCRAM, SoundSource.BLOCKS, 2.0F, 1.0F);
			}
			Fission.LOGGER.info("Reactor at {} SCRAM: {}", this.worldPosition.toShortString(), why);
		}
	}

	/** Clears a SCRAM once the rods are all in and nothing calls for one any more. */
	public boolean reset() {
		if (this.scram && this.rods > 0.995 && this.tripCondition() == null) {
			this.scram = false;
			this.trip = "";
			this.rodTarget = 1;
			return true;
		}
		return false;
	}

	public void setRodTarget(double t) {
		if (!this.scram) {
			this.rodTarget = Math.clamp(t, 0, 1);
			this.auto = false;
		}
	}

	public void setAuto(boolean on) {
		if (!this.scram) {
			this.auto = on;
			if (on) {
				this.setpoint = Math.max(this.thermalPower(), 1000);
			}
		}
	}

	// ------------------------------------------------------------------ tick

	public static void serverTick(Level level, BlockPos pos, BlockState state, ReactorControllerBlockEntity r) {
		if (level instanceof ServerLevel sl) {
			r.tick(sl);
		}
	}

	private void tick(ServerLevel level) {
		long now = level.getGameTime();
		if (this.destroyed) {
			return;
		}
		if (this.core == null || now >= this.nextScan) {
			this.rescan(level);
			this.nextScan = now + 40;
		}
		CoreModel core = this.core;
		if (core == null || !core.valid()) {
			this.power = 0;
			this.precursor = 0;
			if (now % 20 == 0) {
				this.publish(level, now);
			}
			return;
		}
		int n = core.fuel.size();
		if (level.hasNeighborSignal(this.worldPosition)) {
			this.scram("Redstone SCRAM signal");
		}
		// control rods: the drives move them 0.5 %/s (about 100 pcm/s in a typical core), a SCRAM drops them in three seconds
		double speed = this.scram ? 0.35 : 0.005;
		double target = this.scram ? 1 : this.rodTarget;
		this.rods += Math.clamp(target - this.rods, -speed * DT, speed * DT);
		if (this.fuelDirty || now % 100 == 0) {
			this.refreshFuel(level);
		}
		boolean anyFuel = false;
		for (double e : this.eta) {
			anyFuel |= e > 0;
		}
		// neutronics
		double cap = this.capacity();
		double levelFrac = Math.clamp(this.water / cap, 0, 1);
		double boil = Math.min(1, this.steamRate * LATENT_HEAT / this.nominal());
		this.voidFraction = Math.clamp(1 - levelFrac + 0.25 * levelFrac * boil, 0, 1);
		double wet = 1 - this.voidFraction;
		if (this.fuelDirty || Math.abs(this.rods - this.solvedRods) > 1e-5 || Math.abs(wet - this.solvedWater) > 1e-4 || now - this.solvedAt >= 100) {
			core.solve(this.rods, wet, this.eta, this.poison);
			this.solvedRods = this.rods;
			this.solvedWater = wet;
			this.solvedAt = now;
			this.fuelDirty = false;
		}
		double k = core.k;
		this.xenonWorth = XENON_WORTH * this.xenon / XE_EQ_FULL;
		this.rho = k > 0 ? (k - 1) / k - DOPPLER * (this.fuelTemp - 20) - this.xenonWorth : -1;
		// point kinetics, prompt jump approximation below prompt critical
		if (this.rho < BETA * 0.999) {
			this.precursor *= Math.exp(LAMBDA * this.rho / (BETA - this.rho) * DT);
			this.power = this.precursor * BETA / (BETA - this.rho);
		} else {
			this.power *= Math.exp((this.rho - BETA) / GENERATION_TIME * DT);
			this.precursor += LAMBDA * (this.power - this.precursor) * DT;
		}
		if (anyFuel) {
			// the start-up neutron source keeps a few watts going in a shut-down core
			this.precursor = Math.max(this.precursor, 0.5);
			this.power = Math.max(this.power, this.precursor * BETA / Math.max(BETA, BETA - this.rho));
		} else {
			this.precursor *= 0.5;
			this.power *= 0.5;
		}
		this.period = this.rho > 1e-6 ? (this.rho < BETA ? (BETA - this.rho) / (LAMBDA * this.rho) : GENERATION_TIME / (this.rho - BETA))
				: this.rho < -1e-6 ? (BETA - this.rho) / (LAMBDA * this.rho) : Double.POSITIVE_INFINITY;
		// decay heat and xenon run in game time
		double g = Nuclear.GAME_SECONDS_PER_TICK;
		for (int i = 0; i < 3; i++) {
			this.decay[i] += (DECAY_FRACTION[i] * this.power - this.decay[i]) * (1 - Math.exp(-g / DECAY_TAU[i]));
		}
		double phi = this.power / this.nominal();
		this.iodine += (0.064 * phi - LAMBDA_I * this.iodine) * g;
		this.xenon += (0.0025 * phi + LAMBDA_I * this.iodine - LAMBDA_XE * this.xenon - 3 * LAMBDA_XE * phi * this.xenon) * g;
		this.xenon = Math.max(0, this.xenon);
		this.thermal(level, n, cap);
		this.hotTemp = this.waterTemp + (this.fuelTemp - this.waterTemp) * Math.max(1, core.peaking);
		// accidents
		if (this.pressure > 150) {
			ReactorExplosion.explode(level, this, 0.5, "pressure vessel rupture");
			return;
		}
		if (this.power > 25 * this.nominal()) {
			ReactorExplosion.explode(level, this, ReactorExplosion.severity(this.rho, BETA), "prompt critical power excursion");
			return;
		}
		if (this.hotTemp > MELT_TEMP) {
			this.melt(level);
			if (this.destroyed) {
				return;
			}
		}
		// protection system
		String why = this.tripCondition();
		if (why != null && this.rps) {
			this.scram(why);
		}
		if (this.auto && !this.scram) {
			this.regulate();
		}
		for (int i = 0; i < n && i < this.energy.length; i++) {
			this.energy[i] += this.power * core.share[i];
		}
		if (now % 10 == 0) {
			FluidNetwork.solve(level, this);
		}
		this.alarms = this.computeAlarms();
		if ((this.alarms & ~this.lastAlarms) != 0) {
			level.playSound(null, this.worldPosition, ModSounds.ALARM, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
		this.lastAlarms = this.alarms;
		if (now % 20 == 0) {
			this.burnup(level, now);
			this.publish(level, now);
		}
		if (now % 10 == 0) {
			this.history[this.historyPos] = (float) this.thermalPower();
			this.historyPos = (this.historyPos + 1) % this.history.length;
		}
		if (now % 4 == 0) {
			ReactorNetwork.sendStatus(level, this);
		}
		if (this.ventRate > 0 && now % 4 == 0) {
			for (BlockPos v : core.reliefs) {
				level.sendParticles(ParticleTypes.CLOUD, v.getX() + 0.5, v.getY() + 1.1, v.getZ() + 0.5, 6, 0.15, 0.3, 0.15, 0.08);
				if (now % 20 == 0) {
					level.playSound(null, v, ModSounds.RELIEF, SoundSource.BLOCKS, 1.5F, 1.0F);
				}
			}
		}
	}

	private void thermal(ServerLevel level, int n, double cap) {
		double nominal = this.nominal();
		double u = nominal / 500;
		double heatCapacity = 6 * u;
		double tsat = 100 * Math.pow(Math.max(1, this.pressure), 0.25);
		double pth = this.thermalPower();
		double q;
		if (this.water > 1) {
			double ueff = u * (0.03 + 0.97 * Math.min(1, this.water / cap));
			q = ueff * (this.fuelTemp - this.waterTemp);
		} else {
			// dry core: only radiation and steam carry a little heat away
			q = u * 0.005 * (this.fuelTemp - 100);
		}
		this.fuelTemp = Math.max(20, this.fuelTemp + (pth - q) / heatCapacity * DT);
		double gen = 0;
		if (this.water > 1) {
			// the water heats up (scaled down so a start-up does not take hours), then boils
			double cw = this.water * 4186 / 50;
			this.waterTemp += q * DT / cw;
			if (this.waterTemp > tsat) {
				gen = (this.waterTemp - tsat) * cw / LATENT_HEAT / DT;
				this.waterTemp = tsat;
			}
		} else {
			this.waterTemp = Math.max(this.waterTemp, tsat);
		}
		gen = Math.max(0, Math.min(gen, this.water / DT));
		this.water -= gen * DT;
		this.steam += gen * DT;
		this.steamRate = gen;
		// feedwater comes in cold
		double feed = Math.min(this.feedRate * DT, cap - this.water);
		if (feed > 0) {
			this.waterTemp = (this.waterTemp * this.water + 25 * feed) / (this.water + feed);
			this.water += feed;
		}
		double out = Math.min(this.steamOutRate * DT, Math.max(0, this.steam - this.steamAt(1.5)));
		this.steam -= out;
		this.pressure = 1 + 69 * this.steam / (n * STEAM_REF_PER_CHANNEL);
		this.ventRate = 0;
		if (this.pressure > 85 && this.core != null && !this.core.reliefs.isEmpty()) {
			double vent = Math.min(15 * this.core.reliefs.size() * DT, this.steam - this.steamAt(80));
			if (vent > 0) {
				this.steam -= vent;
				this.ventRate = vent / DT;
			}
			this.pressure = 1 + 69 * this.steam / (n * STEAM_REF_PER_CHANNEL);
		}
	}

	/** Why the protection system would SCRAM right now, or null. */
	public @Nullable String tripCondition() {
		double nominal = this.nominal();
		double pth = this.thermalPower();
		if (this.power > 1.15 * nominal) return "High power: " + Math.round(this.power / nominal * 100) + " %";
		if (this.period > 0 && this.period < 10 && this.power > 0.001 * nominal) return String.format(java.util.Locale.ROOT, "Short period: %.1f s", this.period);
		if (this.hotTemp > 1500) return "High fuel temperature: " + Math.round(this.hotTemp) + " °C";
		if (this.water < 0.5 * this.capacity() && (this.rods < 0.99 || pth > 0.01 * nominal)) return "Low water level: " + Math.round(this.water / this.capacity() * 100) + " %";
		if (this.pressure > 95) return "High pressure: " + Math.round(this.pressure) + " bar";
		return null;
	}

	/**
	 * Automatic regulating rods: hold the thermal power setpoint, never faster than about a 33 s period. The wanted
	 * reactivity follows the power error; the rods are steered towards it from where they are, using an estimated
	 * rod worth of 20 000 pcm for the full stroke.
	 */
	private void regulate() {
		double p = Math.max(1, this.thermalPower());
		double err = Math.log(p / Math.max(1, this.setpoint));
		double want = Math.clamp(-0.0015 * err / 0.2, -0.003, 0.0018);
		double move = Math.clamp(-(want - this.rho) / 0.2, -0.02, 0.02);
		this.rodTarget = Math.clamp(this.rods + move, 0, 1);
	}

	/** Test and debug hook: put the chain reaction straight at this many watts (precursors in equilibrium). */
	public void primePower(double watts) {
		this.power = watts;
		this.precursor = watts;
		for (int i = 0; i < 3; i++) {
			this.decay[i] = DECAY_FRACTION[i] * watts;
		}
	}

	private int computeAlarms() {
		double nominal = this.nominal();
		int a = 0;
		if (this.scram) a |= Alarm.SCRAM.bit();
		if (!this.rps) a |= Alarm.RPS_BYPASSED.bit();
		if (this.power > nominal) a |= Alarm.HIGH_POWER.bit();
		if (this.period > 0 && this.period < 30 && this.power > 0.0005 * nominal) a |= Alarm.SHORT_PERIOD.bit();
		if (this.hotTemp > 1200) a |= Alarm.HIGH_FUEL_TEMP.bit();
		if (this.water < 0.7 * this.capacity()) a |= Alarm.LOW_WATER.bit();
		if (this.pressure > 80) a |= Alarm.HIGH_PRESSURE.bit();
		if (this.ventRate > 0) a |= Alarm.RELIEF_OPEN.bit();
		if (this.steamRate > 0.05 && this.feedRate < this.steamRate * 0.5 && this.water < this.capacity() * 0.95) a |= Alarm.FEEDWATER_LOW.bit();
		if (this.xenonWorth > 0.005) a |= Alarm.XENON.bit();
		if (this.rho > BETA * 0.5) a |= Alarm.HIGH_REACTIVITY.bit();
		if (this.meltedChannels > 0) a |= Alarm.CORE_DAMAGE.bit();
		return a;
	}

	// ------------------------------------------------------------------ core and fuel

	private void rescan(ServerLevel level) {
		CoreModel fresh = CoreModel.scan(level, this.worldPosition);
		if (this.core == null || !fresh.same(this.core)) {
			double[] oldEnergy = this.energy;
			this.core = fresh;
			this.energy = new double[fresh.fuel.size()];
			if (oldEnergy.length == this.energy.length) {
				System.arraycopy(oldEnergy, 0, this.energy, 0, oldEnergy.length);
			}
			this.fuelDirty = true;
			this.setChanged();
		}
	}

	public void markFuelDirty() {
		this.fuelDirty = true;
		if (this.core != null) {
			this.core.invalidate();
		}
	}

	private void refreshFuel(ServerLevel level) {
		CoreModel core = this.core;
		int n = core.fuel.size();
		double[] e = new double[n];
		double[] p = new double[n];
		boolean changed = this.eta.length != n;
		for (int i = 0; i < n; i++) {
			if (level.getBlockEntity(core.fuel.get(i)) instanceof FuelChannelBlockEntity ch && !ch.rod().isEmpty()) {
				FuelData d = FuelRodItem.data(ch.rod());
				e[i] = d.type().eta(d.fraction());
				p[i] = FuelType.poison(d.fraction());
				ch.inReactor = level.getGameTime();
			}
			if (!changed && (e[i] > 0) != (this.eta[i] > 0)) {
				changed = true;
			}
		}
		this.eta = e;
		this.poison = p;
		if (changed) {
			core.invalidate();
		}
		this.fuelDirty = true;
	}

	/** Every second: book the energy into the fuel rods, which builds their fission product inventory. */
	private void burnup(ServerLevel level, long now) {
		CoreModel core = this.core;
		double dose = 0;
		for (int i = 0; i < core.fuel.size(); i++) {
			if (level.getBlockEntity(core.fuel.get(i)) instanceof FuelChannelBlockEntity ch && !ch.rod().isEmpty()) {
				ItemStack rod = ch.rod();
				FuelData d = FuelRodItem.data(rod);
				double watts = i < this.energy.length ? this.energy[i] / 20 : 0;
				FuelData next = d.irradiate(watts, 20, now);
				rod.set(ModComponents.FUEL, next);
				ch.setChanged();
				dose += next.radsAtOneMetre(now);
			}
		}
		java.util.Arrays.fill(this.energy, 0);
		this.rodDose = dose;
		// the running core's neutron and gamma field plus what its fuel holds, shielded by everything around it
		float rads = (float) (200 * this.thermalPower() / 1e6 + 0.3 * dose);
		RadiationApi.setEmitter(level, core.center, rads, RadiationApi.radiusFor(rads));
		// rod position indicators
		int ins = (int) Math.round(this.rods * 8);
		for (BlockPos r : core.rods) {
			BlockState s = level.getBlockState(r);
			if (s.getBlock() instanceof ControlRodBlock && s.getValue(ControlRodBlock.INSERTION) != ins) {
				level.setBlock(r, s.setValue(ControlRodBlock.INSERTION, ins), Block.UPDATE_CLIENTS);
			}
		}
		for (int i = 0; i < core.fuel.size(); i++) {
			BlockPos p = core.fuel.get(i);
			if (level.getBlockEntity(p) instanceof FuelChannelBlockEntity ch) {
				ch.setGlow(core.share.length > i && this.power * core.share[i] > 0.02 * Nuclear.CHANNEL_WATTS);
			}
		}
	}

	private void melt(ServerLevel level) {
		CoreModel core = this.core;
		int n = core.fuel.size();
		int melted = 0;
		for (int i = 0; i < n && melted < 2; i++) {
			double t = this.waterTemp + (this.fuelTemp - this.waterTemp) * core.share[i] * n;
			if (t > MELT_TEMP) {
				BlockPos p = core.fuel.get(i);
				double dose = 0;
				if (level.getBlockEntity(p) instanceof FuelChannelBlockEntity ch && !ch.rod().isEmpty()) {
					dose = FuelRodItem.data(ch.rod()).radsAtOneMetre(level.getGameTime());
					ch.clearContent();
				}
				Corium.spawn(level, p, dose);
				melted++;
				this.meltedChannels++;
			}
		}
		if (melted > 0) {
			Fission.LOGGER.warn("Reactor at {}: {} fuel channels melted", this.worldPosition.toShortString(), melted);
			this.scram("Core damage: fuel melting");
			this.markFuelDirty();
			this.nextScan = 0;
			this.rescan(level);
		}
	}

	public void markDestroyed() {
		this.destroyed = true;
		this.power = 0;
		this.precursor = 0;
		this.setChanged();
	}

	/** Every second: comparator, client copy for the annunciators and core maps. */
	private void publish(ServerLevel level, long now) {
		CoreModel core = this.core;
		if (core != null && core.valid()) {
			this.mapW = Math.min(32, core.maxX - core.minX + 1);
			this.mapH = Math.min(32, core.maxZ - core.minZ + 1);
			byte[] m = new byte[this.mapW * this.mapH];
			java.util.Arrays.fill(m, (byte) -1);
			int n = core.fuel.size();
			for (int i = 0; i < n; i++) {
				BlockPos p = core.fuel.get(i);
				int x = p.getX() - core.minX;
				int z = p.getZ() - core.minZ;
				if (x < this.mapW && z < this.mapH) {
					int v = this.eta.length > i && this.eta[i] > 0 ? (int) Math.min(120, core.share[i] * n * 40 * Math.min(1.5, this.thermalPower() / this.nominal() + 0.05)) : 0;
					int idx = z * this.mapW + x;
					m[idx] = (byte) Math.max(m[idx], v);
				}
			}
			this.map = m;
		} else {
			this.mapW = this.mapH = 0;
			this.map = new byte[0];
		}
		level.updateNeighbourForOutputSignal(this.worldPosition, this.getBlockState().getBlock());
		this.setChanged();
		level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
	}

	// ------------------------------------------------------------------ persistence and sync

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.precursor = input.getDoubleOr("precursor", 0);
		this.power = input.getDoubleOr("power", 0);
		this.rods = input.getDoubleOr("rods", 1);
		this.rodTarget = input.getDoubleOr("rod_target", 1);
		this.scram = input.getBooleanOr("scram", false);
		this.trip = input.getStringOr("trip", "");
		this.auto = input.getBooleanOr("auto", false);
		this.setpoint = input.getDoubleOr("setpoint", 1e6);
		this.rps = input.getBooleanOr("rps", true);
		this.fuelTemp = input.getDoubleOr("fuel_temp", 20);
		this.waterTemp = input.getDoubleOr("water_temp", 20);
		this.water = input.getDoubleOr("water", 0);
		this.steam = input.getDoubleOr("steam", 0);
		this.pressure = input.getDoubleOr("pressure", 1);
		this.iodine = input.getDoubleOr("iodine", 0);
		this.xenon = input.getDoubleOr("xenon", 0);
		for (int i = 0; i < 3; i++) {
			this.decay[i] = input.getDoubleOr("decay" + i, 0);
		}
		this.destroyed = input.getBooleanOr("destroyed", false);
		this.meltedChannels = input.getIntOr("melted", 0);
		this.alarms = input.getIntOr("alarms", 0);
		this.mapW = input.getIntOr("map_w", 0);
		this.mapH = input.getIntOr("map_h", 0);
		this.map = input.getIntArray("map").map(a -> {
			byte[] b = new byte[a.length];
			for (int i = 0; i < a.length; i++) b[i] = (byte) a[i];
			return b;
		}).orElse(new byte[0]);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putDouble("precursor", this.precursor);
		output.putDouble("power", this.power);
		output.putDouble("rods", this.rods);
		output.putDouble("rod_target", this.rodTarget);
		output.putBoolean("scram", this.scram);
		output.putString("trip", this.trip);
		output.putBoolean("auto", this.auto);
		output.putDouble("setpoint", this.setpoint);
		output.putBoolean("rps", this.rps);
		output.putDouble("fuel_temp", this.fuelTemp);
		output.putDouble("water_temp", this.waterTemp);
		output.putDouble("water", this.water);
		output.putDouble("steam", this.steam);
		output.putDouble("pressure", this.pressure);
		output.putDouble("iodine", this.iodine);
		output.putDouble("xenon", this.xenon);
		for (int i = 0; i < 3; i++) {
			output.putDouble("decay" + i, this.decay[i]);
		}
		output.putBoolean("destroyed", this.destroyed);
		output.putInt("melted", this.meltedChannels);
		output.putInt("alarms", this.alarms);
		output.putInt("map_w", this.mapW);
		output.putInt("map_h", this.mapH);
		int[] m = new int[this.map.length];
		for (int i = 0; i < m.length; i++) m[i] = this.map[i];
		output.putIntArray("map", m);
	}

	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return this.saveCustomOnly(registries);
	}
}
