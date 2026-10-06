package net.antwire.fission.block;

import net.antwire.fission.world.PipeConnectable;
import net.antwire.fission.world.PipeMedium;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Condenser for the turbine bypass: takes the steam the turbines do not need once the reactor is above 70 bar.
 * 20 kg/s with cooling water next to it (a river, the sea, a pond), 3 kg/s air-cooled.
 */
public class CondenserBlock extends Block implements PipeConnectable {
	public CondenserBlock(Properties properties) {
		super(properties);
	}

	public static double capacity(Level level, BlockPos pos) {
		for (Direction d : Direction.values()) {
			if (level.getFluidState(pos.relative(d)).is(FluidTags.WATER)) {
				return 20;
			}
		}
		return 3;
	}

	@Override
	public boolean connectsPipe(BlockState state, PipeMedium medium, Direction side) {
		return medium == PipeMedium.STEAM;
	}
}
