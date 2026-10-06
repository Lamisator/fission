package net.antwire.fission.block;

import net.antwire.fission.reactor.CoreBlock;
import net.antwire.fission.reactor.CoreRole;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** A boron carbide control rod in its water-cooled channel. The reactor sets how far it is inserted (0 = out, 8 = all in). */
public class ControlRodBlock extends Block implements CoreBlock {
	public static final IntegerProperty INSERTION = IntegerProperty.create("insertion", 0, 8);

	public ControlRodBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(INSERTION, 8));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(INSERTION);
	}

	@Override
	public CoreRole role() {
		return CoreRole.ROD;
	}
}
