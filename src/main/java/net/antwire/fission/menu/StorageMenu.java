package net.antwire.fission.menu;

import net.antwire.fission.block.entity.RtgBlockEntity;
import net.antwire.fission.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Rows of filtered slots over the player inventory (one, two or six rows of nine; the generator's four in one row). */
public class StorageMenu extends AbstractContainerMenu {
	private final Container container;
	public final int rows;
	public final int size;
	/** Top of the player inventory. */
	public final int inventoryY;
	/** The generator's output in centiwatts; empty for the other stores. */
	public final ContainerData data;

	public static StorageMenu create(int id, Inventory inventory, Container container) {
		int size = container.getContainerSize();
		return size > 18 ? new StorageMenu(ModMenus.STORAGE_6, id, inventory, container, 54, new SimpleContainerData(0))
				: size > 9 ? new StorageMenu(ModMenus.STORAGE_2, id, inventory, container, 18, new SimpleContainerData(0))
				: new StorageMenu(ModMenus.STORAGE_1, id, inventory, container, 9, new SimpleContainerData(0));
	}

	public static StorageMenu oneRow(int id, Inventory inventory) {
		return new StorageMenu(ModMenus.STORAGE_1, id, inventory, new SimpleContainer(9), 9, new SimpleContainerData(0));
	}

	public static StorageMenu twoRows(int id, Inventory inventory) {
		return new StorageMenu(ModMenus.STORAGE_2, id, inventory, new SimpleContainer(18), 18, new SimpleContainerData(0));
	}

	public static StorageMenu sixRows(int id, Inventory inventory) {
		return new StorageMenu(ModMenus.STORAGE_6, id, inventory, new SimpleContainer(54), 54, new SimpleContainerData(0));
	}

	public static StorageMenu rtg(int id, Inventory inventory) {
		return rtg(id, inventory, new SimpleContainer(RtgBlockEntity.SLOTS), new SimpleContainerData(1));
	}

	public static StorageMenu rtg(int id, Inventory inventory, Container container, ContainerData data) {
		return new StorageMenu(ModMenus.RTG, id, inventory, container, RtgBlockEntity.SLOTS, data);
	}

	public StorageMenu(MenuType<?> type, int id, Inventory inventory, Container container, int slots, ContainerData data) {
		super(type, id);
		this.container = container;
		this.size = slots;
		this.rows = (slots + 8) / 9;
		this.data = data;
		this.inventoryY = this.rows <= 3 ? 84 : 87 + (this.rows - 3) * 18;
		int cols = Math.min(9, slots);
		int left = 8 + (9 - cols) * 9;
		for (int i = 0; i < slots; i++) {
			this.addSlot(new Slot(container, i, left + i % 9 * 18, 20 + i / 9 * 18) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return container.canPlaceItem(this.getContainerSlot(), stack);
				}

				@Override
				public int getMaxStackSize() {
					return 1;
				}
			});
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, this.inventoryY + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inventory, col, 8 + col * 18, this.inventoryY + 58));
		}
		this.addDataSlots(data);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		int n = this.size;
		if (index < n) {
			if (!this.moveItemStackTo(stack, n, n + 36, true)) {
				return ItemStack.EMPTY;
			}
		} else if (!this.moveItemStackTo(stack, 0, n, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return copy;
	}

	@Override
	public boolean stillValid(Player player) {
		return this.container.stillValid(player);
	}
}
