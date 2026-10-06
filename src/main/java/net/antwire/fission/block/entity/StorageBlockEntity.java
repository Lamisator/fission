package net.antwire.fission.block.entity;

import dev.radiation.api.RadiationApi;
import net.antwire.fission.menu.StorageMenu;
import net.antwire.fission.nuclear.Dose;
import net.antwire.fission.world.NuclearContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Fuel rack, holding basin and storage drum: a filtered inventory that radiates what its shielding lets through. */
public abstract class StorageBlockEntity extends BaseContainerBlockEntity implements NuclearContainer {
	protected NonNullList<ItemStack> items;
	public double contentsRads;

	protected StorageBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int size) {
		super(type, pos, state);
		this.items = NonNullList.withSize(size, ItemStack.EMPTY);
	}

	public abstract boolean allowed(ItemStack stack);

	/** Fraction of the contents' radiation that gets out. */
	protected abstract double leak();

	protected void storageTick(ServerLevel level, long now) {
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, StorageBlockEntity s) {
		if (!(level instanceof ServerLevel sl) || (level.getGameTime() + pos.asLong()) % 40 != 0) {
			return;
		}
		long now = level.getGameTime();
		double rads = 0;
		for (ItemStack st : s.items) {
			rads += Dose.radsAtOneMetre(st, now);
		}
		s.contentsRads = rads;
		float out = (float) (rads * s.leak());
		RadiationApi.setEmitter(sl, pos, out > 0.005F ? out : 0, RadiationApi.radiusFor(out));
		s.storageTick(sl, now);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return this.allowed(stack);
	}

	@Override
	public boolean acceptsFromTube(ItemStack stack) {
		if (!this.allowed(stack)) {
			return false;
		}
		for (ItemStack st : this.items) {
			if (st.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void insertFromTube(ItemStack one) {
		for (int i = 0; i < this.items.size(); i++) {
			if (this.items.get(i).isEmpty()) {
				this.items.set(i, one);
				this.setChanged();
				return;
			}
		}
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	public int getContainerSize() {
		return this.items.size();
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
		return StorageMenu.create(id, inventory, this);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(this.items.size(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.items);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items, true);
	}
}
