package net.antwire.fission.block;

import net.antwire.fission.block.entity.SteamTurbineBlockEntity;
import net.antwire.fission.registry.ModBlockEntities;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Steam turbine. Its shaft points at {@code FACING}, where the generator goes; steam pipes join any other side. */
public class SteamTurbineBlock extends BaseEntityBlock implements PipeConnectable {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty SPINNING = BooleanProperty.create("spinning");

	public SteamTurbineBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SPINNING, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, SPINNING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	public boolean connectsPipe(BlockState state, PipeMedium medium, Direction side) {
		return medium == PipeMedium.STEAM && side != state.getValue(FACING);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SteamTurbineBlockEntity t) {
			player.sendOverlayMessage(Component.translatable("block.fission.steam_turbine.status", Math.round(t.rpm),
					String.format(java.util.Locale.ROOT, "%.2f", t.steamIn), String.format(java.util.Locale.ROOT, "%.0f", t.pressure),
					String.format(java.util.Locale.ROOT, "%.2f", t.electric / 1e6)));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SteamTurbineBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return createTickerHelper(type, ModBlockEntities.STEAM_TURBINE, level.isClientSide() ? SteamTurbineBlockEntity::clientTick : SteamTurbineBlockEntity::serverTick);
	}
}
