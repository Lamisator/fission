package net.antwire.fission.world;

import net.antwire.fission.item.FuelRodItem;
import net.antwire.fission.registry.ModComponents;
import net.antwire.fission.registry.ModItems;
import net.minecraft.world.item.ItemStack;

/** Pneumatic fuel transfer tubes carry fuel assemblies; shielded isotope pipes carry canisters and other radioactive material. */
public enum TubeKind {
	FUEL, ISOTOPE;

	public boolean carries(ItemStack stack) {
		return switch (this) {
			case FUEL -> stack.getItem() instanceof FuelRodItem;
			case ISOTOPE -> stack.has(ModComponents.CANISTER) || stack.is(ModItems.PLUTONIUM) || stack.is(ModItems.DEPLETED_URANIUM)
					|| stack.is(ModItems.DECAYED_CANISTER) || stack.is(ModItems.CORIUM_FRAGMENT) || stack.is(ModItems.GRAPHITE_DEBRIS);
		};
	}
}
