package net.antwire.fission.block;

import net.antwire.fission.reactor.CoreBlock;
import net.antwire.fission.reactor.CoreRole;
import net.minecraft.world.level.block.Block;

/** Graphite, reflector and vessel blocks, feedwater inlets, steam outlets and relief valves: plain blocks with a role in the core. */
public class CorePartBlock extends Block implements CoreBlock, net.antwire.fission.world.PipeConnectable {
	private final CoreRole role;

	public CorePartBlock(CoreRole role, Properties properties) {
		super(properties);
		this.role = role;
	}

	@Override
	public CoreRole role() {
		return this.role;
	}

	@Override
	public boolean connectsPipe(net.minecraft.world.level.block.state.BlockState state, net.antwire.fission.world.PipeMedium medium,
			net.minecraft.core.Direction side) {
		return this.role == CoreRole.INLET && medium == net.antwire.fission.world.PipeMedium.WATER
				|| this.role == CoreRole.OUTLET && medium == net.antwire.fission.world.PipeMedium.STEAM;
	}
}
