package net.antwire.fission.nuclear;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A sealed canister holding {@code atoms} atoms of one isotope, as of game time {@code time} (ticks). */
public record Canister(Isotope isotope, double atoms, long time) {
	public static final Codec<Canister> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.xmap(Isotope::byId, Isotope::id).fieldOf("isotope").forGetter(Canister::isotope),
			Codec.DOUBLE.fieldOf("atoms").forGetter(Canister::atoms),
			Codec.LONG.fieldOf("time").forGetter(Canister::time)
	).apply(i, Canister::new));
	public static final StreamCodec<ByteBuf, Canister> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Isotope.values()[i], Isotope::ordinal), Canister::isotope,
			ByteBufCodecs.DOUBLE, Canister::atoms,
			ByteBufCodecs.VAR_LONG, Canister::time,
			Canister::new);

	/** Below this a canister counts as decayed and harmless, TBq. */
	public static final double DECAYED_TBQ = 1e-4;

	public double atoms(long now) {
		return this.isotope.decay(this.atoms, Nuclear.gameSeconds(now - this.time));
	}

	public double tbq(long now) {
		return this.isotope.tbq(this.atoms(now));
	}

	public double radsAtOneMetre(long now) {
		return this.isotope.radsAtOneMetre(this.tbq(now));
	}

	public boolean decayed(long now) {
		return this.tbq(now) < DECAYED_TBQ;
	}

	public Canister aged(long now) {
		return now <= this.time ? this : new Canister(this.isotope, this.atoms(now), now);
	}
}
