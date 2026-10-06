package net.antwire.fission.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Control room equipment wired to a reactor controller with the data cable. The link is synced to clients. */
public class LinkedBlockEntity extends BlockEntity {
	private @Nullable BlockPos reactor;

	public LinkedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public @Nullable BlockPos reactor() {
		return this.reactor;
	}

	public @Nullable ReactorControllerBlockEntity controller() {
		return this.reactor != null && this.level != null && this.level.isLoaded(this.reactor)
				&& this.level.getBlockEntity(this.reactor) instanceof ReactorControllerBlockEntity c ? c : null;
	}

	public void link(BlockPos reactor) {
		this.reactor = reactor.immutable();
		this.setChanged();
		if (this.level != null) {
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		long l = input.getLongOr("reactor", Long.MIN_VALUE);
		this.reactor = l == Long.MIN_VALUE ? null : BlockPos.of(l);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (this.reactor != null) {
			output.putLong("reactor", this.reactor.asLong());
		}
	}

	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return this.saveCustomOnly(registries);
	}
}
