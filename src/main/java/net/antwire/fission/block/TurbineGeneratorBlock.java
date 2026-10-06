package net.antwire.fission.block;

import net.antwire.fission.block.entity.SteamTurbineBlockEntity;
import net.antwire.gridworks.grid.GridEntry;
import net.antwire.gridworks.grid.GridHooks;
import net.antwire.gridworks.grid.GridMachineBlock;
import net.antwire.gridworks.grid.Terminal;
import net.antwire.gridworks.grid.VoltageClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import java.util.List;

/**
 * The turbine's generator: 5 MW at 10 kV into Gridworks once the turbine is synchronised. It faces the turbine; its
 * other sides are 10 kV terminals (MV cable, or a substation transformer straight onto it).
 */
public class TurbineGeneratorBlock extends Block implements GridMachineBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty ON = BooleanProperty.create("on");

	public TurbineGeneratorBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, ON);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction face = context.getHorizontalDirection();
		// face the turbine if one is next to us
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (context.getLevel().getBlockState(context.getClickedPos().relative(d)).getBlock() instanceof SteamTurbineBlock) {
				face = d;
				break;
			}
		}
		return this.defaultBlockState().setValue(FACING, face);
	}

	@Override
	public GridEntry gridEntry(BlockState state) {
		return GridEntry.machine().allFaces(VoltageClass.MV_10KV).face(state.getValue(FACING), null);
	}

	@Override
	public void terminals(ServerLevel level, BlockPos pos, BlockState state, VoltageClass voltage, List<Terminal> out) {
		if (level.getBlockEntity(pos.relative(state.getValue(FACING))) instanceof SteamTurbineBlockEntity t) {
			out.add(Terminal.producer(t.offerWatts(), w -> {
				t.electric = w;
				boolean on = w > 1000;
				if (state.getValue(ON) != on && level.getBlockState(pos).is(this)) {
					level.setBlock(pos, level.getBlockState(pos).setValue(ON, on), Block.UPDATE_CLIENTS);
				}
			}));
		}
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!oldState.is(this)) {
			GridHooks.placed(level, pos, state);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		GridHooks.removed(level, pos);
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}
}
