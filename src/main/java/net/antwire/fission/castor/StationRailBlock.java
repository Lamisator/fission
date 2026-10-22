package net.antwire.fission.castor;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A straight rail where a running shunting locomotive stops. It goes on when the gantry crane over its wagons is done,
 * when the rail is powered (a lever, a button, a detector rail further on...), or - with no crane at work - when the
 * waiting time is up. Use it with an empty hand to change the waiting time. For any other cart it is a plain rail;
 * power spreads along a row of station rails like along powered rails.
 */
public class StationRailBlock extends PoweredRailBlock {
	public static final IntegerProperty WAIT = IntegerProperty.create("wait", 0, 4);
	private static final int[] SECONDS = {5, 15, 30, 60, 120};

	public StationRailBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.defaultBlockState().setValue(WAIT, 2));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(WAIT);
	}

	public static int waitTicks(BlockState state) {
		return SECONDS[state.getValue(WAIT)] * 20;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			BlockState next = state.cycle(WAIT);
			level.setBlock(pos, next, Block.UPDATE_ALL);
			player.sendOverlayMessage(Component.translatable("block.fission.station_rail.wait", SECONDS[next.getValue(WAIT)]));
		}
		return InteractionResult.SUCCESS;
	}
}
