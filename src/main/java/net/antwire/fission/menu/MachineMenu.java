package net.antwire.fission.menu;

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

/** Input slot on the left, outputs on the right in rows of four, progress and power state. */
public class MachineMenu extends AbstractContainerMenu {
	private final Container container;
	private final ContainerData data;
	public final int outputs;

	public static MachineMenu create(int id, Inventory inventory, Container container, ContainerData data, int outputs) {
		return new MachineMenu(outputs > 2 ? ModMenus.REPROCESSING : ModMenus.CENTRIFUGE, id, inventory, container, data, outputs);
	}

	public static MachineMenu reprocessing(int id, Inventory inventory) {
		return new MachineMenu(ModMenus.REPROCESSING, id, inventory, new SimpleContainer(21), new SimpleContainerData(3), 20);
	}

	public static MachineMenu centrifuge(int id, Inventory inventory) {
		return new MachineMenu(ModMenus.CENTRIFUGE, id, inventory, new SimpleContainer(3), new SimpleContainerData(3), 2);
	}

	public MachineMenu(MenuType<?> type, int id, Inventory inventory, Container container, ContainerData data, int outputs) {
		super(type, id);
		this.container = container;
		this.data = data;
		this.outputs = outputs;
		this.addSlot(new Slot(container, 0, 26, 35) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return container.canPlaceItem(0, stack);
			}
		});
		for (int i = 0; i < outputs; i++) {
			int col = outputs > 4 ? i % 5 : i, row = outputs > 4 ? i / 5 : 0;
			this.addSlot(new Slot(container, 1 + i, (outputs > 4 ? 80 : 98) + col * 18, (outputs > 4 ? 8 : 35) + row * 18) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return false;
				}
			});
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inventory, col, 8 + col * 18, 142));
		}
		this.addDataSlots(data);
	}

	public float progress() {
		int max = this.data.get(1);
		return max > 0 ? Math.min(1, this.data.get(0) / (float) max) : 0;
	}

	public boolean powered() {
		return this.data.get(2) != 0;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		int n = 1 + this.outputs;
		if (index < n) {
			if (!this.moveItemStackTo(stack, n, n + 36, true)) {
				return ItemStack.EMPTY;
			}
		} else if (!this.moveItemStackTo(stack, 0, 1, false)) {
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
