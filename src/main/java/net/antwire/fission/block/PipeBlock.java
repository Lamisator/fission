package net.antwire.fission.block;

import net.antwire.fission.world.PipeConnectable;
import net.antwire.fission.world.PipeMedium;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/** Feedwater pipe (blue) or insulated steam pipe (silver). */
public class PipeBlock extends ConduitBlock {
	private final PipeMedium medium;

	public PipeBlock(PipeMedium medium, Properties properties) {
		super(medium == PipeMedium.STEAM ? 8 : 6, properties);
		this.medium = medium;
	}

	public PipeMedium medium() {
		return this.medium;
	}

	@Override
	protected boolean connects(LevelReader level, BlockPos pos, Direction d) {
		BlockState n = level.getBlockState(pos.relative(d));
		if (n.getBlock() instanceof PipeBlock p) {
			return p.medium == this.medium;
		}
		return n.getBlock() instanceof PipeConnectable c && c.connectsPipe(n, this.medium, d.getOpposite());
	}
}
