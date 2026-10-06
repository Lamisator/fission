package net.antwire.fission.block.entity;

import dev.radiation.api.RadiationApi;
import net.antwire.fission.block.FuelChannelBlock;
import net.antwire.fission.item.FuelRodItem;
import net.antwire.fission.nuclear.Dose;
import net.antwire.fission.registry.ModBlockEntities;
import net.antwire.fission.world.NuclearContainer;
import net.antwire.fission.world.TubeKind;
import net.antwire.fission.world.Tubes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** One fuel assembly in its pressure tube. Spent assemblies leave by fuel transfer tube on their own (on-load refuelling). */
public class FuelChannelBlockEntity extends BlockEntity implements NuclearContainer, Clearable {
	private ItemStack rod = ItemStack.EMPTY;
	/** Game time a reactor last counted this channel; outside a reactor the channel radiates on its own. */
	public long inReactor = -1_000_000;

	public FuelChannelBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.FUEL_CHANNEL, pos, state);
	}

	public ItemStack rod() {
		return this.rod;
	}

	public void setRod(ItemStack stack) {
		this.rod = stack;
		this.changedContents();
	}

	public ItemStack takeRod() {
		ItemStack r = this.rod;
		this.rod = ItemStack.EMPTY;
		this.changedContents();
		return r;
	}

	@Override
	public void clearContent() {
		this.rod = ItemStack.EMPTY;
		this.changedContents();
	}

	private void changedContents() {
		this.setChanged();
		if (this.level instanceof ServerLevel sl) {
			int s = this.rod.isEmpty() ? 0 : FuelRodItem.spent(this.rod) ? 2 : 1;
			BlockState st = this.getBlockState();
			if (st.getBlock() instanceof FuelChannelBlock && st.getValue(FuelChannelBlock.LOAD) != s) {
				sl.setBlock(this.worldPosition, st.setValue(FuelChannelBlock.LOAD, s), Block.UPDATE_CLIENTS);
			}
		}
	}

	public void setGlow(boolean glow) {
		BlockState st = this.getBlockState();
		if (this.level != null && st.getBlock() instanceof FuelChannelBlock && st.getValue(FuelChannelBlock.GLOW) != glow) {
			this.level.setBlock(this.worldPosition, st.setValue(FuelChannelBlock.GLOW, glow), Block.UPDATE_CLIENTS);
		}
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, FuelChannelBlockEntity ch) {
		if (!(level instanceof ServerLevel sl) || (level.getGameTime() + pos.asLong()) % 40 != 0) {
			return;
		}
		long now = level.getGameTime();
		boolean inCore = now - ch.inReactor < 200;
		if (!inCore) {
			ch.setGlow(false);
			float rads = (float) Dose.radsAtOneMetre(ch.rod, now);
			RadiationApi.setEmitter(sl, pos, rads > 0.01F ? rads : 0, RadiationApi.radiusFor(rads));
		} else {
			RadiationApi.removeEmitter(sl, pos);
		}
		if (!ch.rod.isEmpty() && FuelRodItem.spent(ch.rod)) {
			if (Tubes.push(sl, pos, ch.rod, TubeKind.FUEL)) {
				ch.takeRod();
			}
		}
	}

	@Override
	public boolean acceptsFromTube(ItemStack stack) {
		return this.rod.isEmpty() && stack.getItem() instanceof FuelRodItem && !FuelRodItem.spent(stack);
	}

	@Override
	public void insertFromTube(ItemStack one) {
		this.setRod(one);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.rod = input.read("rod", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!this.rod.isEmpty()) {
			output.store("rod", ItemStack.OPTIONAL_CODEC, this.rod);
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
