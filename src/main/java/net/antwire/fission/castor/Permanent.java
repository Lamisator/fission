package net.antwire.fission.castor;

import net.antwire.fission.block.entity.StorageBlockEntities;
import net.antwire.fission.item.FuelRodItem;
import net.antwire.fission.nuclear.Canister;
import net.antwire.fission.nuclear.FuelData;
import net.antwire.fission.registry.ModComponents;
import net.antwire.fission.registry.ModItems;
import net.minecraft.world.item.ItemStack;

/**
 * What goes to permanent storage, and so into a CASTOR and the final repository: long-lived isotopes, plutonium,
 * depleted uranium, debris of an accident, and spent fuel that has cooled in a holding basin for at least a day.
 * Short-lived isotopes decay in the basin instead; fresh fuel and empty canisters are not waste.
 */
public final class Permanent {
	private Permanent() {
	}

	public static boolean accepts(ItemStack stack, long now) {
		if (stack.isEmpty()) {
			return false;
		}
		Canister c = stack.get(ModComponents.CANISTER);
		if (c != null) {
			return !c.isotope().shortLived();
		}
		if (stack.getItem() instanceof FuelRodItem) {
			FuelData d = stack.get(ModComponents.FUEL);
			return d != null && d.irradiated() && now - d.time() >= StorageBlockEntities.COOLING_TICKS;
		}
		return stack.is(ModItems.PLUTONIUM) || stack.is(ModItems.DEPLETED_URANIUM) || stack.is(ModItems.CORIUM_FRAGMENT)
				|| stack.is(ModItems.GRAPHITE_DEBRIS) || stack.is(ModItems.FUEL_FRAGMENT);
	}
}
