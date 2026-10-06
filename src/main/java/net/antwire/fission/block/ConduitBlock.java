package net.antwire.fission.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

/** Shared shape and connection logic of pipes and tubes. */
public abstract class ConduitBlock extends Block {
	private final int thickness;
	private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

	protected ConduitBlock(int thickness, Properties properties) {
		super(properties);
		this.thickness = thickness;
		BlockState s = this.stateDefinition.any();
		for (BooleanProperty p : net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.values()) {
			s = s.setValue(p, false);
		}
		this.registerDefaultState(s);
	}

	public int thickness() {
		return this.thickness;
	}

	protected abstract boolean connects(LevelReader level, BlockPos pos, Direction d);

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.values().toArray(new BooleanProperty[0]));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return this.shapes.computeIfAbsent(state, s -> {
			double lo = 8 - this.thickness / 2.0;
			double hi = 8 + this.thickness / 2.0;
			VoxelShape shape = Block.box(lo, lo, lo, hi, hi, hi);
			for (Direction d : Direction.values()) {
				if (s.getValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(d))) {
					double x0 = d.getStepX() < 0 ? 0 : d.getStepX() > 0 ? hi : lo;
					double x1 = d.getStepX() < 0 ? lo : d.getStepX() > 0 ? 16 : hi;
					double y0 = d.getStepY() < 0 ? 0 : d.getStepY() > 0 ? hi : lo;
					double y1 = d.getStepY() < 0 ? lo : d.getStepY() > 0 ? 16 : hi;
					double z0 = d.getStepZ() < 0 ? 0 : d.getStepZ() > 0 ? hi : lo;
					double z1 = d.getStepZ() < 0 ? lo : d.getStepZ() > 0 ? 16 : hi;
					shape = Shapes.or(shape, Block.box(x0, y0, z0, x1, y1, z1));
				}
			}
			return shape;
		});
	}

	public BlockState withConnections(LevelReader level, BlockPos pos, BlockState state) {
		for (Direction d : Direction.values()) {
			state = state.setValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(d), this.connects(level, pos, d));
		}
		return state;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.withConnections(context.getLevel(), context.getClickedPos(), this.defaultBlockState());
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return state.setValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(direction), this.connects(level, pos, direction));
	}
}
