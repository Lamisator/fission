package net.antwire.fission.block.entity;

import dev.radiation.api.RadiationApi;
import net.antwire.fission.block.FuelChannelBlock;
import net.antwire.fission.item.FuelRodItem;
import net.antwire.fission.nuclear.Dose;
import net.antwire.fission.nuclear.FuelData;
import net.antwire.fission.nuclear.FuelType;
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

/**
 * One fuel assembly in its pressure tube. On-load refuelling works like the refuelling machine of a pressure tube
 * reactor: in a running core a spent assembly stays in its channel until a fresh one comes down the transfer tube, then
 * the two are exchanged and the fresh assembly is lowered into the core over {@link #REFUEL_SECONDS}, so the reactivity
 * rises by a few pcm a second instead of jumping. The spent assembly leaves by tube; outside a reactor spent fuel leaves
 * on its own.
 */
public class FuelChannelBlockEntity extends BlockEntity implements NuclearContainer, Clearable {
	/** Real seconds the refuelling machine takes to lower a fresh assembly into a running core. */
	public static final double REFUEL_SECONDS = 180;
	private ItemStack rod = ItemStack.EMPTY;
	/** The spent assembly taken out by the refuelling machine, waiting for the transfer tube. */
	private ItemStack outgoing = ItemStack.EMPTY;
	/** Game time a reactor last counted this channel; outside a reactor the channel radiates on its own. */
	public long inReactor = -1_000_000;
	/** How far the fresh assembly is in (1 = fully); the reactor sees eta and poison blended from what was there before. */
	private double blend = 1;
	private double fromEta;
	private double fromPoison;

	public FuelChannelBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.FUEL_CHANNEL, pos, state);
	}

	public ItemStack rod() {
		return this.rod;
	}

	public void setRod(ItemStack stack) {
		this.load(stack, 0, 0);
	}

	/** Puts an assembly in; in a running core it goes in slowly, starting from these values of the old contents. */
	private void load(ItemStack stack, double oldEta, double oldPoison) {
		this.rod = stack;
		if (this.level != null && this.level.getGameTime() - this.inReactor < 200) {
			this.blend = 0;
			this.fromEta = oldEta;
			this.fromPoison = oldPoison;
		} else {
			this.blend = 1;
		}
		this.changedContents();
	}

	/** True while a fresh assembly is still being lowered in. */
	public boolean refuelling() {
		return !this.rod.isEmpty() && this.blend < 1;
	}

	/** Eta the reactor sees in this channel: the rod's own, blended with the old contents while it goes in. Never 0 with a rod in. */
	public double effectiveEta(double eta) {
		return Math.max(1e-6, this.blend >= 1 ? eta : this.fromEta + (eta - this.fromEta) * this.blend);
	}

	public double effectivePoison(double poison) {
		return this.blend >= 1 ? poison : this.fromPoison + (poison - this.fromPoison) * this.blend;
	}

	public ItemStack takeRod() {
		ItemStack r = this.rod;
		this.rod = ItemStack.EMPTY;
		this.changedContents();
		return r;
	}

	/** The spent assembly the refuelling machine still holds, removed. */
	public ItemStack takeOutgoing() {
		ItemStack r = this.outgoing;
		this.outgoing = ItemStack.EMPTY;
		this.setChanged();
		return r;
	}

	@Override
	public void clearContent() {
		this.rod = ItemStack.EMPTY;
		this.outgoing = ItemStack.EMPTY;
		this.blend = 1;
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
		if (ch.blend < 1) {
			ch.blend = Math.min(1, ch.blend + 1 / (20 * REFUEL_SECONDS));
			if (ch.blend >= 1 || level.getGameTime() % 100 == 0) {
				ch.setChanged();
			}
		}
		if (!(level instanceof ServerLevel sl) || (level.getGameTime() + pos.asLong()) % 40 != 0) {
			return;
		}
		long now = level.getGameTime();
		boolean inCore = now - ch.inReactor < 200;
		if (!ch.outgoing.isEmpty() && Tubes.push(sl, pos, ch.outgoing, TubeKind.FUEL)) {
			ch.outgoing = ItemStack.EMPTY;
			ch.setChanged();
		}
		if (!inCore) {
			ch.setGlow(false);
			float rads = (float) Dose.radsAtOneMetre(ch.rod, now);
			RadiationApi.setEmitter(sl, pos, rads > 0.01F ? rads : 0, RadiationApi.radiusFor(rads));
		} else {
			RadiationApi.removeEmitter(sl, pos);
		}
		// in a running core the spent assembly waits for its replacement (see insertFromTube)
		if (!inCore && !ch.rod.isEmpty() && FuelRodItem.spent(ch.rod)) {
			if (Tubes.push(sl, pos, ch.rod, TubeKind.FUEL)) {
				ch.takeRod();
			}
		}
	}

	@Override
	public boolean acceptsFromTube(ItemStack stack) {
		boolean room = this.rod.isEmpty() || this.outgoing.isEmpty() && this.blend >= 1 && FuelRodItem.spent(this.rod);
		return room && stack.getItem() instanceof FuelRodItem && !FuelRodItem.spent(stack);
	}

	@Override
	public void insertFromTube(ItemStack one) {
		if (this.rod.isEmpty()) {
			this.setRod(one);
			return;
		}
		// exchange: the spent assembly comes out as the fresh one goes in
		FuelData old = FuelRodItem.data(this.rod);
		this.outgoing = this.rod;
		this.load(one, old.type().eta(old.fraction()), FuelType.poison(old.fraction()));
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.rod = input.read("rod", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		this.outgoing = input.read("outgoing", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		this.blend = input.getDoubleOr("blend", 1);
		this.fromEta = input.getDoubleOr("from_eta", 0);
		this.fromPoison = input.getDoubleOr("from_poison", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!this.rod.isEmpty()) {
			output.store("rod", ItemStack.OPTIONAL_CODEC, this.rod);
		}
		if (!this.outgoing.isEmpty()) {
			output.store("outgoing", ItemStack.OPTIONAL_CODEC, this.outgoing);
		}
		if (this.blend < 1) {
			output.putDouble("blend", this.blend);
			output.putDouble("from_eta", this.fromEta);
			output.putDouble("from_poison", this.fromPoison);
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
