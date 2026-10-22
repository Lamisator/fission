package net.antwire.fission.castor;

import net.antwire.fission.menu.StorageMenu;
import net.antwire.fission.nuclear.Dose;
import net.antwire.fission.registry.ModCastor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A flatcar with a CASTOR cask on it: a forged cast-iron cask with a bolted double lid and cooling fins, the transport
 * and storage cask for spent fuel and high-level waste. It holds 18 items of what goes to permanent storage (see
 * {@link Permanent}). The walls let through only {@link #LEAK} of what is inside; breaking the wagon spills it all.
 */
public class CastorWagon extends AbstractMinecartContainer {
	/**
	 * Fraction of the contents' radiation that gets through 40 cm of ductile cast iron with neutron moderator rods.
	 * A cask full of freshly cooled assemblies gives a few hundredths of a rad/s at one metre; real casks of fuel
	 * cooled for years stay below 0.2 mSv/h at the surface.
	 */
	public static final double LEAK = 1.0e-6;
	public static final int SLOTS = 18;
	/** What gets out, rads per second at one metre (server side, updated once a second). */
	public double outsideRads;

	public CastorWagon(EntityType<? extends CastorWagon> type, Level level) {
		super(type, level);
	}

	@Override
	public void tick() {
		super.tick();
		// the radiating spot follows the cask: renewed four times a second while it travels, once a second standing
		boolean moving = this.getDeltaMovement().horizontalDistanceSqr() > 1.0e-4;
		if (this.level() instanceof ServerLevel sl && (this.tickCount + this.getId()) % (moving ? 5 : 20) == 0) {
			long now = sl.getGameTime();
			double rads = 0;
			for (int i = 0; i < this.getContainerSize(); i++) {
				rads += Dose.radsAtOneMetre(this.getItem(i), now);
			}
			this.outsideRads = rads * LEAK;
			CastorRadiation.radiate(sl, CastorRadiation.key(this), this.position().add(0, 0.8, 0), (float) this.outsideRads);
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		if (!this.level().isClientSide()) {
			CastorRadiation.end(CastorRadiation.key(this));
		}
		super.remove(reason);
	}

	@Override
	public int getContainerSize() {
		return SLOTS;
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return Permanent.accepts(stack, this.level().getGameTime());
	}

	/** Whether the crane can put this item in now (it is waste for permanent storage and a place is free). */
	public boolean accepts(ItemStack stack) {
		return this.canPlaceItem(0, stack) && this.freeSlot() >= 0;
	}

	public void insert(ItemStack one) {
		int slot = this.freeSlot();
		if (slot >= 0) {
			this.setItem(slot, one);
		}
	}

	public int freeSlot() {
		for (int i = 0; i < this.getContainerSize(); i++) {
			if (this.getItem(i).isEmpty()) {
				return i;
			}
		}
		return -1;
	}

	public boolean empty() {
		return this.isEmpty();
	}

	@Override
	protected Item getDropItem() {
		return ModCastor.CASTOR_WAGON_ITEM;
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(ModCastor.CASTOR_WAGON_ITEM);
	}

	@Override
	public BlockState getDefaultDisplayBlockState() {
		return ModCastor.CASTOR_CASK.defaultBlockState();
	}

	@Override
	public int getDefaultDisplayOffset() {
		return 7;
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return StorageMenu.create(containerId, inventory, this);
	}

	@Override
	protected Component getTypeName() {
		return Component.translatable("entity.fission.castor_wagon");
	}
}
