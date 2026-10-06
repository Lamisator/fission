package net.antwire.fission.block.entity;

import net.antwire.fission.item.CanisterItem;
import net.antwire.fission.item.FuelRodItem;
import net.antwire.fission.nuclear.FuelData;
import net.antwire.fission.nuclear.Isotope;
import net.antwire.fission.registry.ModBlockEntities;
import net.antwire.fission.registry.ModComponents;
import net.antwire.fission.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class Machines {
	private Machines() {
	}

	/** Gas centrifuge cascade: four yellowcake into one low-enriched uranium and three depleted, 2 kW, 20 s. */
	public static class Centrifuge extends MachineBlockEntity {
		public Centrifuge(BlockPos pos, BlockState state) {
			super(ModBlockEntities.CENTRIFUGE, pos, state, 2);
		}

		@Override
		protected int duration() {
			return 400;
		}

		@Override
		protected double watts() {
			return 2000;
		}

		@Override
		protected int inputCount() {
			return 4;
		}

		@Override
		protected boolean acceptsInput(ItemStack stack) {
			return stack.is(ModItems.YELLOWCAKE);
		}

		@Override
		protected boolean ready(ItemStack input, long now) {
			return input.getCount() >= 4 && this.items.get(1).getCount() < 64 && this.items.get(2).getCount() <= 61;
		}

		@Override
		protected List<ItemStack> results(ItemStack input, long now) {
			// stacks onto what is there: handled by a custom insert below
			return List.of();
		}

		@Override
		protected Component getDefaultName() {
			return Component.translatable("block.fission.gas_centrifuge");
		}

		/** Centrifuge outputs stack, so it does its own bookkeeping instead of the generic one-item-per-slot outputs. */
		@Override
		protected boolean finishRun(ItemStack input) {
			this.addTo(1, new ItemStack(ModItems.ENRICHED_URANIUM));
			this.addTo(2, new ItemStack(ModItems.DEPLETED_URANIUM, 3));
			input.shrink(4);
			return true;
		}

		private void addTo(int slot, ItemStack stack) {
			ItemStack have = this.items.get(slot);
			if (have.isEmpty()) {
				this.items.set(slot, stack);
			} else {
				have.grow(stack.getCount());
			}
		}
	}

	/**
	 * PUREX reprocessing: dissolves a spent fuel assembly that has cooled for a Minecraft day and separates it into one
	 * canister per fission product (as much as the rod really holds), plutonium for MOX fuel and depleted uranium.
	 * 3 kW, 15 s, behind heavy shielding (5 % gets out).
	 */
	public static class Reprocessing extends MachineBlockEntity {
		public Reprocessing(BlockPos pos, BlockState state) {
			super(ModBlockEntities.REPROCESSING, pos, state, 20);
		}

		@Override
		protected int duration() {
			return 300;
		}

		@Override
		protected double watts() {
			return 3000;
		}

		@Override
		protected int inputCount() {
			return 1;
		}

		@Override
		protected boolean acceptsInput(ItemStack stack) {
			return stack.getItem() instanceof FuelRodItem && FuelRodItem.irradiated(stack);
		}

		@Override
		protected boolean ready(ItemStack input, long now) {
			FuelData d = input.get(ModComponents.FUEL);
			return d != null && now - d.time() >= StorageBlockEntities.COOLING_TICKS;
		}

		@Override
		protected List<ItemStack> results(ItemStack input, long now) {
			FuelData d = FuelRodItem.data(input).aged(now);
			List<ItemStack> out = new ArrayList<>();
			for (Isotope iso : Isotope.values()) {
				if (iso == Isotope.PU239) {
					continue;
				}
				double atoms = d.atoms(iso);
				if (iso.tbq(atoms) >= 1e-3) {
					out.add(CanisterItem.of(ModItems.ISOTOPE_CANISTER, iso, atoms, now));
				}
			}
			out.sort((a, b) -> Double.compare(b.get(ModComponents.CANISTER).radsAtOneMetre(now), a.get(ModComponents.CANISTER).radsAtOneMetre(now)));
			int pu = (int) Math.max(0, Math.round(d.atoms(Isotope.PU239) / 1.0e22));
			if (pu > 0) {
				out.add(new ItemStack(ModItems.PLUTONIUM, Math.min(64, pu)));
			}
			out.add(new ItemStack(ModItems.DEPLETED_URANIUM, 2));
			return out;
		}

		@Override
		protected Component getDefaultName() {
			return Component.translatable("block.fission.reprocessing_plant");
		}
	}
}
