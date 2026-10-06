package net.antwire.fission.client;

import net.antwire.fission.menu.StorageMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Fuel rack, holding basin and storage drum. */
public class StorageScreen extends AbstractContainerScreen<StorageMenu> {
	public StorageScreen(StorageMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x = this.leftPos, y = this.topPos;
		g.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFF3A3E44);
		g.fill(x + 2, y + 2, x + this.imageWidth - 2, y + 4, 0xFFE8C020);
		for (var slot : this.menu.slots) {
			g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, slot.index < this.menu.rows * 9 ? 0xFF1A2430 : 0xFF1B1C1F);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
		g.text(this.font, this.title, 8, 7, 0xFFE8E8E8, false);
		g.text(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY, 0xFFB0B0B0, false);
	}
}
