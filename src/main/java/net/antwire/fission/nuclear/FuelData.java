package net.antwire.fission.nuclear;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * What a fuel rod has been through: fuel type, burnup and the atoms of every tracked fission product and actinide
 * (in {@link Isotope} order), as of game time {@code time} (ticks). Immutable; the reactor and the decay bookkeeping
 * write new copies onto the item.
 */
public record FuelData(FuelType type, double burnup, List<Double> atoms, long time) {
	public static final Codec<FuelData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.xmap(s -> FuelType.valueOf(s.toUpperCase(java.util.Locale.ROOT)), t -> t.name().toLowerCase(java.util.Locale.ROOT))
					.fieldOf("type").forGetter(FuelData::type),
			Codec.DOUBLE.fieldOf("burnup").forGetter(FuelData::burnup),
			Codec.DOUBLE.listOf().fieldOf("atoms").forGetter(FuelData::atoms),
			Codec.LONG.fieldOf("time").forGetter(FuelData::time)
	).apply(i, FuelData::new));
	public static final StreamCodec<ByteBuf, FuelData> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> FuelType.values()[i], FuelType::ordinal), FuelData::type,
			ByteBufCodecs.DOUBLE, FuelData::burnup,
			ByteBufCodecs.DOUBLE.apply(ByteBufCodecs.list()), FuelData::atoms,
			ByteBufCodecs.VAR_LONG, FuelData::time,
			FuelData::new);

	public static FuelData fresh(FuelType type) {
		List<Double> atoms = new ArrayList<>();
		for (int i = 0; i < Isotope.values().length; i++) {
			atoms.add(0.0);
		}
		return new FuelData(type, 0, atoms, 0);
	}

	public double fraction() {
		return this.burnup / this.type.maxBurnup;
	}

	public boolean spent() {
		return this.burnup >= this.type.maxBurnup;
	}

	public boolean irradiated() {
		return this.burnup > 1e-6;
	}

	public double atoms(Isotope iso) {
		return iso.ordinal() < this.atoms.size() ? this.atoms.get(iso.ordinal()) : 0;
	}

	/** Atoms after decaying until {@code now} (ticks). */
	public double atoms(Isotope iso, long now) {
		return iso.decay(this.atoms(iso), Nuclear.gameSeconds(now - this.time));
	}

	/** The same rod after running at {@code watts} for {@code ticks} ticks ending at {@code now}. */
	public FuelData irradiate(double watts, long ticks, long now) {
		double dt = ticks * Nuclear.GAME_SECONDS_PER_TICK;
		double decayFirst = Nuclear.gameSeconds(now - ticks - this.time);
		double fissions = Math.max(0, watts) / Nuclear.JOULES_PER_FISSION;
		List<Double> next = new ArrayList<>(this.atoms.size());
		for (Isotope iso : Isotope.values()) {
			double n = iso.decay(this.atoms(iso), decayFirst);
			double source = switch (iso) {
				case PU239 -> fissions * this.type.conversion * 0.6;
				case AM241 -> fissions * 0.0004;
				default -> fissions * iso.yield;
			};
			// exact solution for a constant source over dt
			double e = Math.exp(-iso.lambda * dt);
			n = n * e + source / iso.lambda * (1 - e);
			next.add(n);
		}
		return new FuelData(this.type, this.burnup + Math.max(0, watts) * dt / (1e6 * 86400), next, now);
	}

	/** The same rod with its inventory decayed to {@code now}. */
	public FuelData aged(long now) {
		if (now <= this.time) {
			return this;
		}
		List<Double> next = new ArrayList<>(this.atoms.size());
		for (Isotope iso : Isotope.values()) {
			next.add(this.atoms(iso, now));
		}
		return new FuelData(this.type, this.burnup, next, now);
	}

	/** Total activity in TBq at {@code now}. */
	public double tbq(long now) {
		double sum = 0;
		for (Isotope iso : Isotope.values()) {
			sum += iso.tbq(this.atoms(iso, now));
		}
		return sum;
	}

	/** Gamma dose rate at one metre, rads per second. */
	public double radsAtOneMetre(long now) {
		double sum = 0;
		for (Isotope iso : Isotope.values()) {
			sum += iso.radsAtOneMetre(iso.tbq(this.atoms(iso, now)));
		}
		return sum;
	}

	/** Decay heat in watts: on average about 0.8 MeV of beta and gamma energy per decay. */
	public double decayWatts(long now) {
		return this.tbq(now) * 1e12 * 0.8 * 1.602e-13;
	}
}
