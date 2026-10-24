package net.antwire.fission.nuclear;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A Pu-238 heat source for the radioisotope generator, made at game time {@code made} (ticks). Pu-238 is an alpha
 * emitter with a half-life of 87.7 years: in game time that is about 445 real days to half power.
 */
public record HeatSource(long made) {
	public static final Codec<HeatSource> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("made").forGetter(HeatSource::made)
	).apply(i, HeatSource::new));
	public static final StreamCodec<ByteBuf, HeatSource> STREAM_CODEC = ByteBufCodecs.VAR_LONG.map(HeatSource::new, HeatSource::made);

	/** Pu-238 half-life in game seconds. */
	public static final double HALF_LIFE = 87.7 * 365.25 * 86400;
	/** Electrical watts of a fresh heat source: about 500 W of decay heat through thermocouples at 6 %. */
	public static final double FRESH_WATTS = 30;
	public static final double THERMAL_WATTS = 500;

	/** Fraction of the fresh output left at {@code now}. */
	public double left(long now) {
		return Math.pow(0.5, Nuclear.gameSeconds(Math.max(0, now - this.made)) / HALF_LIFE);
	}

	public double watts(long now) {
		return FRESH_WATTS * this.left(now);
	}

	/** Real days until the output has halved, from fresh. */
	public static double halfLifeRealDays() {
		return HALF_LIFE / Nuclear.GAME_SECONDS_PER_SECOND / 86400;
	}
}
