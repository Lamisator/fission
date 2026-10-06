package net.antwire.fission.block;

import net.antwire.fission.block.entity.FeedwaterPumpBlockEntity;
import net.antwire.fission.world.PipeConnectable;
import net.antwire.fission.world.PipeMedium;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Feedwater pump: put it in or next to water, run a water pipe to the reactor's feedwater inlet and feed it 10 kV. */
public class FeedwaterPumpBlock extends BaseEntityBlock implements PipeConnectable {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty ON = BooleanProperty.create("on");

	public FeedwaterPumpBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, ON);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public boolean connectsPipe(BlockState state, PipeMedium medium, Direction side) {
		return medium == PipeMedium.WATER;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FeedwaterPumpBlockEntity p) {
			player.sendOverlayMessage(Component.translatable("block.fission.feedwater_pump.status",
					Component.translatable(p.powered() ? "fission.yes" : "fission.no"), Component.translatable(p.hasWater() ? "fission.yes" : "fission.no"),
					String.format(java.util.Locale.ROOT, "%.1f", p.flow())));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FeedwaterPumpBlockEntity(pos, state);
	}
}
