package net.antwire.fission.block;

import net.antwire.fission.block.entity.RtgBlockEntity;
import net.antwire.fission.block.entity.StorageBlockEntity;
import net.antwire.fission.registry.ModBlockEntities;
import net.antwire.gridworks.grid.GridEntry;
import net.antwire.gridworks.grid.GridHooks;
import net.antwire.gridworks.grid.GridMachineBlock;
import net.antwire.gridworks.grid.Terminal;
import net.antwire.gridworks.grid.VoltageClass;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** The radioisotope generator: a finned drum that feeds any 12 V DC cable or battery it touches. */
public class RtgBlock extends ContainerBlock implements GridMachineBlock {
	public RtgBlock(Properties properties) {
		super(RtgBlockEntity::new, () -> ModBlockEntities.RTG, (BlockEntityTicker<StorageBlockEntity>) StorageBlockEntity::serverTick,
				Block.box(2, 0, 2, 14, 14, 14), properties);
	}

	@Override
	public GridEntry gridEntry(BlockState state) {
		return GridEntry.machine().allFaces(VoltageClass.DC_12V);
	}

	@Override
	public void terminals(ServerLevel level, BlockPos pos, BlockState state, VoltageClass voltage, List<Terminal> out) {
		if (level.getBlockEntity(pos) instanceof RtgBlockEntity rtg) {
			double w = rtg.watts();
			if (w > 0.01) {
				out.add(Terminal.producer(w, d -> {
				}));
			}
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
