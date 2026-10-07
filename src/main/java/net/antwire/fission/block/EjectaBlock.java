package net.antwire.fission.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What an exploding core throws out: irradiated graphite and pieces of fuel. It radiates where it lands, and the
 * graphite comes down burning, so it can set roofs and fields on fire.
 */
public class EjectaBlock extends RadiatingBlock implements Fallable {
	private final float fireChance;

	public EjectaBlock(float radsAtOneMetre, float fireChance, Properties properties) {
		super(radsAtOneMetre, properties);
		this.fireChance = fireChance;
	}

	@Override
	public void onLand(Level level, BlockPos pos, BlockState state, BlockState replacedBlock, FallingBlockEntity entity) {
		if (!(level instanceof ServerLevel sl)) {
			return;
		}
		sl.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.02);
		BlockPos above = pos.above();
		if (sl.getRandom().nextFloat() < this.fireChance && sl.getBlockState(above).isAir() && BaseFireBlock.canBePlacedAt(sl, above, net.minecraft.core.Direction.UP)) {
			sl.setBlock(above, BaseFireBlock.getState(sl, above), Block.UPDATE_ALL);
		}
	}
}
