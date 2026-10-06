package net.antwire.fission.client;

import net.antwire.fission.menu.MachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Reprocessing plant and gas centrifuge. */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
	public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x = this.leftPos, y = this.topPos;
		g.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFF3A3E44);
		g.fill(x + 2, y + 2, x + this.imageWidth - 2, y + 4, 0xFF40A0E0);
		for (var slot : this.menu.slots) {
			g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF1B1C1F);
		}
		// progress arrow
		int px = x + 48, py = y + 39;
		g.fill(px, py, px + 26, py + 8, 0xFF1B1C1F);
		g.fill(px + 1, py + 1, px + 1 + Math.round(24 * this.menu.progress()), py + 7, 0xFF50E070);
		g.fill(x + 26, y + 56, x + 42, y + 60, this.menu.powered() ? 0xFF50E070 : 0xFF802020);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
		g.text(this.font, this.title, 8, 6, 0xFFE8E8E8, false);
		g.text(this.font, Component.translatable(this.menu.powered() ? "screen.fission.powered" : "screen.fission.no_power"), 8, 62,
				this.menu.powered() ? 0xFF50E070 : 0xFFE05050, false);
		g.text(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY, 0xFFB0B0B0, false);
	}
}
