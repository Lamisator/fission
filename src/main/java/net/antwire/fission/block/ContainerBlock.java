package net.antwire.fission.block;

import net.antwire.fission.world.TubeConnectable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Storage blocks and machines: open their menu, drop their contents, tick on the server, take tubes. */
public class ContainerBlock extends BaseEntityBlock implements TubeConnectable {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private final BiFunction<BlockPos, BlockState, BlockEntity> factory;
	private final Supplier<BlockEntityType<?>> type;
	private final BlockEntityTicker<BlockEntity> ticker;
	private final VoxelShape shape;

	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> ContainerBlock(BiFunction<BlockPos, BlockState, BlockEntity> factory, Supplier<BlockEntityType<?>> type,
			BlockEntityTicker<T> ticker, VoxelShape shape, Properties properties) {
		super(properties);
		this.factory = factory;
		this.type = type;
		this.ticker = (BlockEntityTicker<BlockEntity>) ticker;
		this.shape = shape;
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
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return this.shape == null ? Shapes.block() : this.shape;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MenuProvider menu) {
			player.openMenu(menu);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof Container c) {
			Containers.dropContents(level, pos, c);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		dev.radiation.api.RadiationApi.removeEmitter(level, pos);
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return level.getBlockEntity(pos) instanceof Container c ? net.minecraft.world.inventory.AbstractContainerMenu.getRedstoneSignalFromContainer(c) : 0;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return this.factory.apply(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() || type != this.type.get() ? null : (BlockEntityTicker<T>) this.ticker;
	}
}
