package net.antwire.fission.block;

import net.antwire.fission.world.TubeConnectable;
import net.antwire.fission.world.TubeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/** Pneumatic fuel transfer tube, or lead-shielded isotope pipe. */
public class TubeBlock extends ConduitBlock {
	private final TubeKind kind;

	public TubeBlock(TubeKind kind, Properties properties) {
		super(kind == TubeKind.FUEL ? 6 : 5, properties);
		this.kind = kind;
	}

	public TubeKind kind() {
		return this.kind;
	}

	@Override
	protected boolean connects(LevelReader level, BlockPos pos, Direction d) {
		BlockState n = level.getBlockState(pos.relative(d));
		if (n.getBlock() instanceof TubeBlock t) {
			return t.kind == this.kind;
		}
		return n.getBlock() instanceof TubeConnectable;
	}
}
