package net.antwire.fission.menu;

import net.antwire.fission.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** One or two rows of filtered slots over the player inventory. */
public class StorageMenu extends AbstractContainerMenu {
	private final Container container;
	public final int rows;

	public static StorageMenu create(int id, Inventory inventory, Container container) {
		return container.getContainerSize() > 9 ? new StorageMenu(ModMenus.STORAGE_2, id, inventory, container, 2)
				: new StorageMenu(ModMenus.STORAGE_1, id, inventory, container, 1);
	}

	public static StorageMenu oneRow(int id, Inventory inventory) {
		return new StorageMenu(ModMenus.STORAGE_1, id, inventory, new SimpleContainer(9), 1);
	}

	public static StorageMenu twoRows(int id, Inventory inventory) {
		return new StorageMenu(ModMenus.STORAGE_2, id, inventory, new SimpleContainer(18), 2);
	}

	public StorageMenu(MenuType<?> type, int id, Inventory inventory, Container container, int rows) {
		super(type, id);
		this.container = container;
		this.rows = rows;
		for (int r = 0; r < rows; r++) {
			for (int c = 0; c < 9; c++) {
				this.addSlot(new Slot(container, c + r * 9, 8 + c * 18, 20 + r * 18) {
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
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inventory, col, 8 + col * 18, 142));
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		int n = this.rows * 9;
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
