package net.antwire.fission.block.entity;

import net.antwire.fission.item.HeatSourceItem;
import net.antwire.fission.menu.StorageMenu;
import net.antwire.fission.nuclear.HeatSource;
import net.antwire.fission.registry.ModBlockEntities;
import net.antwire.fission.registry.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Radioisotope thermoelectric generator: four Pu-238 heat sources warm thermocouples, about 30 W each at 12 V DC. No
 * moving parts, no fuel to feed: the output only falls as the plutonium decays, to half in about 445 real days.
 */
public class RtgBlockEntity extends StorageBlockEntity {
	public static final int SLOTS = 4;
	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int i) {
			return i == 0 ? (int) Math.round(RtgBlockEntity.this.watts() * 100) : 0;
		}

		@Override
		public void set(int i, int value) {
		}

		@Override
		public int getCount() {
			return 1;
		}
	};

	public RtgBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.RTG, pos, state, SLOTS);
	}

	/** Electrical output now, watts. */
	public double watts() {
		long now = this.level == null ? 0 : this.level.getGameTime();
		double w = 0;
		for (ItemStack st : this.items) {
			if (st.getItem() instanceof HeatSourceItem) {
				w += HeatSourceItem.watts(st, now);
			}
		}
		return w;
	}

	@Override
	public boolean allowed(ItemStack stack) {
		return stack.getItem() instanceof HeatSourceItem;
	}

	/** The casing stops the alpha particles; a little gamma and neutron radiation gets out. */
	@Override
	protected double leak() {
		return 0.5;
	}

	@Override
	protected void storageTick(ServerLevel level, long now) {
		for (ItemStack st : this.items) {
			if (st.getItem() instanceof HeatSourceItem && !st.has(ModComponents.HEAT_SOURCE)) {
				st.set(ModComponents.HEAT_SOURCE, new HeatSource(now));
				this.setChanged();
			}
		}
	}

	@Override
	protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
		return StorageMenu.rtg(id, inventory, this, this.data);
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.fission.rtg");
	}
}
