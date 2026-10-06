package net.antwire.fission.block.entity;

import net.antwire.fission.item.FuelRodItem;
import net.antwire.fission.nuclear.Canister;
import net.antwire.fission.nuclear.FuelData;
import net.antwire.fission.registry.ModBlockEntities;
import net.antwire.fission.registry.ModComponents;
import net.antwire.fission.registry.ModItems;
import net.antwire.fission.world.TubeKind;
import net.antwire.fission.world.Tubes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** The three storage blocks. */
public final class StorageBlockEntities {
	/** A Minecraft day: how long spent fuel cools in the basin before it goes on to reprocessing. */
	public static final long COOLING_TICKS = 24000;

	private StorageBlockEntities() {
	}

	/** Fresh fuel waiting to be loaded; sends it down fuel transfer tubes to empty fuel channels. */
	public static class FuelRack extends StorageBlockEntity {
		public FuelRack(BlockPos pos, BlockState state) {
			super(ModBlockEntities.FUEL_RACK, pos, state, 9);
		}

		@Override
		public boolean allowed(ItemStack stack) {
			return stack.getItem() instanceof FuelRodItem && !FuelRodItem.irradiated(stack);
		}

		@Override
		protected double leak() {
			return 1;
		}

		@Override
		protected void storageTick(ServerLevel level, long now) {
			for (int i = 0; i < this.items.size(); i++) {
				ItemStack st = this.items.get(i);
				if (!st.isEmpty() && Tubes.push(level, this.worldPosition, st, TubeKind.FUEL)) {
					st.shrink(1);
					this.setChanged();
					return;
				}
			}
		}

		@Override
		protected Component getDefaultName() {
			return Component.translatable("block.fission.fuel_rack");
		}
	}

	/**
	 * A pool of water for short-lived isotopes and spent fuel. Three metres of water absorb nearly everything. Canisters
	 * that have decayed come out as empty, harmless canisters; spent fuel that has cooled for a day goes on to
	 * reprocessing by fuel transfer tube.
	 */
	public static class HoldingBasin extends StorageBlockEntity {
		public HoldingBasin(BlockPos pos, BlockState state) {
			super(ModBlockEntities.HOLDING_BASIN, pos, state, 18);
		}

		@Override
		public boolean allowed(ItemStack stack) {
			Canister c = stack.get(ModComponents.CANISTER);
			if (c != null) {
				return c.isotope().shortLived();
			}
			return stack.getItem() instanceof FuelRodItem && FuelRodItem.irradiated(stack) || stack.is(ModItems.DECAYED_CANISTER);
		}

		@Override
		protected double leak() {
			return 2.0e-4;
		}

		@Override
		protected void storageTick(ServerLevel level, long now) {
			for (int i = 0; i < this.items.size(); i++) {
				ItemStack st = this.items.get(i);
				Canister c = st.get(ModComponents.CANISTER);
				if (c != null && c.decayed(now)) {
					this.items.set(i, new ItemStack(ModItems.DECAYED_CANISTER));
					this.setChanged();
				}
			}
			for (int i = 0; i < this.items.size(); i++) {
				ItemStack st = this.items.get(i);
				FuelData d = st.get(ModComponents.FUEL);
				if (d != null && d.spent() && now - d.time() >= COOLING_TICKS && Tubes.push(level, this.worldPosition, st, TubeKind.FUEL)) {
					st.shrink(1);
					this.setChanged();
					return;
				}
			}
		}

		@Override
		protected Component getDefaultName() {
			return Component.translatable("block.fission.holding_basin");
		}
	}

	/** A shielded drum for long-lived waste: it holds back about 97 % of the radiation, not all of it. */
	public static class StorageDrum extends StorageBlockEntity {
		public StorageDrum(BlockPos pos, BlockState state) {
			super(ModBlockEntities.STORAGE_DRUM, pos, state, 9);
		}

		@Override
		public boolean allowed(ItemStack stack) {
			Canister c = stack.get(ModComponents.CANISTER);
			if (c != null) {
				return !c.isotope().shortLived();
			}
			return stack.is(ModItems.PLUTONIUM) || stack.is(ModItems.DEPLETED_URANIUM) || stack.is(ModItems.CORIUM_FRAGMENT)
					|| stack.is(ModItems.GRAPHITE_DEBRIS) || stack.is(ModItems.DECAYED_CANISTER);
		}

		@Override
		protected double leak() {
			return 0.03;
		}

		@Override
		protected Component getDefaultName() {
			return Component.translatable("block.fission.storage_drum");
		}
	}
}
