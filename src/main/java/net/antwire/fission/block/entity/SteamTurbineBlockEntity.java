package net.antwire.fission.block.entity;

import net.antwire.fission.block.SteamTurbineBlock;
import net.antwire.fission.registry.ModBlockEntities;
import net.antwire.fission.util.ClientHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A steam turbine: spins up to 3000 rpm on 2 kg/s of steam once there are 40 bar, then its generator synchronises and
 * the turbine takes what the generator delivers: 0.7 MJ of electricity per kg of 70 bar steam, a little less at
 * lower pressure. Without steam it runs down.
 */
public class SteamTurbineBlockEntity extends BlockEntity {
	public static final double SYNC_RPM = 2900;
	public static final double JOULES_PER_KG = 0.7e6;
	private static final double IDLE_STEAM = 0.25;
	public double rpm;
	public double steamIn;
	public double pressure;
	public double electric;
	private long fedAt = Long.MIN_VALUE;
	/** Client: whether the whine is playing. */
	public boolean soundPlaying;

	public SteamTurbineBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.STEAM_TURBINE, pos, state);
	}

	public double steamDemand(double pressure) {
		if (pressure < 40) {
			return 0;
		}
		if (this.rpm < SYNC_RPM) {
			return 2.0;
		}
		return Math.min(12, IDLE_STEAM + 0.2 + 1.15 * this.electric / JOULES_PER_KG);
	}

	public void receive(double rate, double pressure) {
		this.steamIn = rate;
		this.pressure = pressure;
		this.fedAt = this.level == null ? 0 : this.level.getGameTime();
	}

	/** What the generator may offer the grid now. */
	public double offerWatts() {
		if (this.rpm < SYNC_RPM) {
			return 0;
		}
		return Math.clamp((this.steamIn - IDLE_STEAM) * JOULES_PER_KG * Math.min(1, this.pressure / 70), 0, 5.0e6);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, SteamTurbineBlockEntity t) {
		if (level.getGameTime() - t.fedAt > 20) {
			t.steamIn = 0;
		}
		double drive = t.steamIn - IDLE_STEAM - t.electric / JOULES_PER_KG;
		if (t.steamIn > IDLE_STEAM && drive >= -0.05) {
			t.rpm = Math.min(3000, t.rpm + 60);
		} else {
			t.rpm = Math.max(0, t.rpm - (t.steamIn > 0 ? 15 : 25));
		}
		if (t.rpm < SYNC_RPM) {
			t.electric = 0;
		}
		boolean spin = t.rpm > 300;
		if (state.getValue(SteamTurbineBlock.SPINNING) != spin) {
			level.setBlock(pos, state.setValue(SteamTurbineBlock.SPINNING, spin), Block.UPDATE_CLIENTS);
		}
		if (spin && level instanceof ServerLevel sl && level.getGameTime() % 10 == 0) {
			sl.sendParticles(ParticleTypes.WHITE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 1, 0.3, 0.0, 0.3, 0.01);
		}
	}

	public static void clientTick(Level level, BlockPos pos, BlockState state, SteamTurbineBlockEntity t) {
		if (state.getValue(SteamTurbineBlock.SPINNING) && !t.soundPlaying) {
			t.soundPlaying = true;
			ClientHooks.turbineSound.accept(t);
		}
	}
}
