package net.antwire.fission.block;

import net.antwire.fission.block.entity.ReactorControllerBlockEntity;
import net.antwire.fission.registry.ModBlockEntities;
import net.antwire.fission.util.ClientHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The reactor's instrumentation and rod drive cabinet. Place it against the core; it finds every connected core block.
 * Right-click for the control panel; a redstone signal SCRAMs the reactor; a comparator reads the power (15 = 100 %).
 */
public class ReactorControllerBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	public ReactorControllerBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			ClientHooks.openReactor.accept(pos);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		if (level.getBlockEntity(pos) instanceof ReactorControllerBlockEntity r && r.channels() > 0) {
			return (int) Math.clamp(Math.round(r.thermalPower() / r.nominal() * 15), 0, 15);
		}
		return 0;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ReactorControllerBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.REACTOR_CONTROLLER, ReactorControllerBlockEntity::serverTick);
	}
}
