package net.antwire.fission.block;

import net.antwire.fission.block.entity.FuelChannelBlockEntity;
import net.antwire.fission.item.FuelRodItem;
import net.antwire.fission.reactor.CoreBlock;
import net.antwire.fission.reactor.CoreRole;
import net.antwire.fission.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** A zircaloy pressure tube for one fuel assembly. Glows Cherenkov blue while its fuel is fissioning. */
public class FuelChannelBlock extends BaseEntityBlock implements CoreBlock {
	public static final IntegerProperty LOAD = IntegerProperty.create("load", 0, 2);
	public static final BooleanProperty GLOW = BooleanProperty.create("glow");

	public FuelChannelBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(LOAD, 0).setValue(GLOW, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LOAD, GLOW);
	}

	@Override
	public CoreRole role() {
		return CoreRole.FUEL;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (stack.getItem() instanceof FuelRodItem && level.getBlockEntity(pos) instanceof FuelChannelBlockEntity ch && ch.rod().isEmpty()) {
			if (!level.isClientSide()) {
				ch.setRod(stack.split(1));
				level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8F, 0.7F);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof FuelChannelBlockEntity ch)) {
			return InteractionResult.SUCCESS;
		}
		if (player.isShiftKeyDown() && !ch.rod().isEmpty()) {
			// pulling an irradiated assembly out by hand is exactly as bad an idea as it sounds
			ItemStack out = ch.takeRod();
			if (!player.addItem(out)) {
				net.minecraft.world.Containers.dropItemStack(level, player.getX(), player.getY(), player.getZ(), out);
			}
			level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.8F, 0.7F);
		} else {
			player.sendOverlayMessage(ch.rod().isEmpty() ? Component.translatable("block.fission.fuel_channel.empty")
					: Component.translatable("block.fission.fuel_channel.holds", ch.rod().getHoverName()));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(GLOW) && random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.GLOW, pos.getX() + random.nextDouble(), pos.getY() + 1.02, pos.getZ() + random.nextDouble(), 0, 0.01, 0);
		}
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FuelChannelBlockEntity ch && !ch.rod().isEmpty()) {
			Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, ch.takeRod());
		}
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FuelChannelBlockEntity ch) {
			ItemStack out = ch.takeOutgoing();
			if (!out.isEmpty()) {
				Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, out);
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FuelChannelBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.FUEL_CHANNEL, FuelChannelBlockEntity::serverTick);
	}
}
