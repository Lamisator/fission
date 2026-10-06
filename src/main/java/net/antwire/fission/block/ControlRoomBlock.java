package net.antwire.fission.block;

import net.antwire.fission.block.entity.LinkedBlockEntity;
import net.antwire.fission.block.entity.ReactorControllerBlockEntity;
import net.antwire.fission.registry.ModBlockEntities;
import net.antwire.fission.registry.ModSounds;
import net.antwire.fission.util.ClientHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Control room equipment wired to a reactor with the data cable: console, SCRAM pedestal, annunciator panel, core map. */
public class ControlRoomBlock extends BaseEntityBlock {
	public enum Kind { CONSOLE, SCRAM, ANNUNCIATOR, CORE_MAP }

	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** SCRAM pedestal: the hinged guard is up. */
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
	public static final BooleanProperty PRESSED = BlockStateProperties.POWERED;
	private final Kind kind;

	public ControlRoomBlock(Kind kind, Properties properties) {
		super(properties);
		this.kind = kind;
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false).setValue(PRESSED, false));
	}

	public Kind kind() {
		return this.kind;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, OPEN, PRESSED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (this.kind) {
			case SCRAM -> Block.box(3, 0, 3, 13, 12, 13);
			case CONSOLE -> Block.box(0, 0, 0, 16, 14, 16);
			default -> Shapes.block();
		};
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		LinkedBlockEntity be = level.getBlockEntity(pos) instanceof LinkedBlockEntity l ? l : null;
		if (be == null) {
			return InteractionResult.PASS;
		}
		switch (this.kind) {
			case CONSOLE -> {
				if (level.isClientSide()) {
					if (be.reactor() != null) {
						ClientHooks.openReactor.accept(be.reactor());
					} else {
						player.sendOverlayMessage(Component.translatable("block.fission.not_linked"));
					}
				}
			}
			case SCRAM -> {
				if (level.isClientSide()) {
					return InteractionResult.SUCCESS;
				}
				if (!state.getValue(OPEN) || player.isShiftKeyDown()) {
					// lift (or lower) the guard first
					level.setBlock(pos, state.setValue(OPEN, !state.getValue(OPEN)), Block.UPDATE_ALL);
					level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6F, 1.4F);
				} else {
					this.press(level, pos, state, be, "Manual SCRAM (" + player.getName().getString() + ")");
				}
			}
			default -> {
				if (!level.isClientSide()) {
					player.sendOverlayMessage(be.reactor() == null ? Component.translatable("block.fission.not_linked")
							: Component.translatable("block.fission.linked_to", be.reactor().toShortString()));
				}
			}
		}
		return InteractionResult.SUCCESS;
	}

	private void press(Level level, BlockPos pos, BlockState state, LinkedBlockEntity be, String why) {
		level.setBlock(pos, state.setValue(PRESSED, true), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 1.0F, 0.6F);
		level.scheduleTick(pos, this, 30);
		ReactorControllerBlockEntity c = be.controller();
		if (c != null) {
			c.scram(why);
		}
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
		if (this.kind == Kind.SCRAM && !level.isClientSide() && level.hasNeighborSignal(pos) && !state.getValue(PRESSED)
				&& level.getBlockEntity(pos) instanceof LinkedBlockEntity be) {
			this.press(level, pos, state, be, "SCRAM by redstone");
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(PRESSED)) {
			level.setBlock(pos, state.setValue(PRESSED, false), Block.UPDATE_ALL);
		}
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new LinkedBlockEntity(switch (this.kind) {
			case CONSOLE -> ModBlockEntities.CONSOLE;
			case SCRAM -> ModBlockEntities.SCRAM_BUTTON;
			case ANNUNCIATOR -> ModBlockEntities.ANNUNCIATOR;
			case CORE_MAP -> ModBlockEntities.CORE_MAP;
		}, pos, state);
	}
}
