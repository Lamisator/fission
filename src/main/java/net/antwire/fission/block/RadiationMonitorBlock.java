package net.antwire.fission.block;

import net.antwire.fission.block.entity.RadiationMonitorBlockEntity;
import net.antwire.fission.registry.ModBlockEntities;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Wall-mounted area radiation monitor. Shows the dose rate in front of it; flashes and beeps above its threshold
 * (sneak-use cycles 0.1 / 1 / 10 / 100 rad/s); a comparator reads it on a log scale (1 = 0.01 rad/s, 15 = 1000 rad/s).
 */
public class RadiationMonitorBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty ALARM = BooleanProperty.create("alarm");

	public RadiationMonitorBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ALARM, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, ALARM);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RadiationMonitorBlockEntity m) {
			if (player.isShiftKeyDown()) {
				m.threshold = (m.threshold + 1) % RadiationMonitorBlockEntity.THRESHOLDS.length;
				m.setChanged();
			}
			player.sendOverlayMessage(Component.translatable("block.fission.radiation_monitor.status",
					String.format(java.util.Locale.ROOT, "%.3f", m.rate), String.valueOf(m.limit())));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		if (level.getBlockEntity(pos) instanceof RadiationMonitorBlockEntity m && m.rate >= 0.01F) {
			return (int) Math.clamp(Math.round((Math.log10(m.rate) + 2) * 14 / 5) + 1, 1, 15);
		}
		return 0;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new RadiationMonitorBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.RADIATION_MONITOR, RadiationMonitorBlockEntity::serverTick);
	}
}
