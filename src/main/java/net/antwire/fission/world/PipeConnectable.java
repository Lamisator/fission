package net.antwire.fission.world;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** A block that water or steam pipes join onto. */
public interface PipeConnectable {
	boolean connectsPipe(BlockState state, PipeMedium medium, Direction side);
}
