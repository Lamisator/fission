package net.antwire.fission.block;

import net.antwire.fission.world.Corium;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Molten corium. See {@link Corium}. */
public class CoriumBlock extends Block {
	public static final IntegerProperty HEAT = IntegerProperty.create("heat", 0, 15);
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 14, 16);

	public CoriumBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(HEAT, 15));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HEAT);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		level.scheduleTick(pos, this, Corium.UPDATE_TICKS);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		Corium.update(level, pos, state);
		if (level.getBlockState(pos).is(this)) {
			level.scheduleTick(pos, this, Corium.UPDATE_TICKS);
		}
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean intersects) {
		if (level instanceof ServerLevel sl) {
			entity.igniteForSeconds(10);
			entity.hurtServer(sl, sl.damageSources().lava(), 20);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(4) == 0) {
			level.addParticle(net.minecraft.core.particles.ParticleTypes.LAVA, pos.getX() + random.nextDouble(), pos.getY() + 0.9,
					pos.getZ() + random.nextDouble(), 0, 0, 0);
		}
	}
}
