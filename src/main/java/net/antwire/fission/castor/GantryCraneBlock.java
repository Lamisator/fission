package net.antwire.fission.castor;

import net.antwire.fission.registry.ModCastor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
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
 * The control cab of a gantry crane. The crane spans {@link GantryCraneBlockEntity#LENGTH} blocks ahead of the cab (the
 * way you face when you place it) and {@link GantryCraneBlockEntity#WIDTH} to the right; its bridge runs along the
 * length on two crane rails, its trolley across the bridge. Use the cab to switch between LOAD (storage into the
 * CASTOR), UNLOAD (CASTOR into storage) and OFF; sneak-use for its status.
 */
public class GantryCraneBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	public GantryCraneBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof GantryCraneBlockEntity crane) {
			if (player.isShiftKeyDown()) {
				player.sendSystemMessage(crane.status());
			} else {
				crane.cycleMode();
				player.sendOverlayMessage(Component.translatable("block.fission.gantry_crane.mode",
						Component.translatable("block.fission.gantry_crane.mode." + crane.mode().name().toLowerCase(java.util.Locale.ROOT))));
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof GantryCraneBlockEntity crane && !crane.carrying().isEmpty()) {
			// whatever hangs on the hook falls where the cab stood
			Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, crane.carrying().copy());
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		dev.radiation.api.RadiationApi.removeEmitter(level, pos);
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new GantryCraneBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (type != ModCastor.GANTRY_CRANE_BE) {
			return null;
		}
		return level.isClientSide() ? null : (BlockEntityTicker<T>) (BlockEntityTicker<GantryCraneBlockEntity>) GantryCraneBlockEntity::serverTick;
	}
}
