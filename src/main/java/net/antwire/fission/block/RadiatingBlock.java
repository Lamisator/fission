package net.antwire.fission.block;

import dev.radiation.api.RadiationApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Solidified corium and reactor debris: they radiate where they lie, for good. */
public class RadiatingBlock extends Block {
	private final float rads;

	public RadiatingBlock(float radsAtOneMetre, Properties properties) {
		super(properties);
		this.rads = radsAtOneMetre;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (level instanceof ServerLevel sl && !oldState.is(this)) {
			RadiationApi.setEmitter(sl, pos, this.rads, RadiationApi.radiusFor(this.rads));
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		RadiationApi.removeEmitter(level, pos);
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (this.rads > 100 && random.nextInt(8) == 0) {
			level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 1.0,
					pos.getZ() + random.nextDouble(), 0, 0.02, 0);
		}
	}
}
