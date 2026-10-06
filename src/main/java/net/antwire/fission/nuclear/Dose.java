package net.antwire.fission.nuclear;

import net.antwire.fission.item.FuelRodItem;
import net.antwire.fission.registry.ModComponents;
import net.antwire.fission.registry.ModItems;
import net.minecraft.world.item.ItemStack;

/** How much an item radiates, in rads per second at one metre. */
public final class Dose {
	private Dose() {
	}

	public static double radsAtOneMetre(ItemStack stack, long now) {
		if (stack.isEmpty()) {
			return 0;
		}
		double each = 0;
		FuelData fuel = stack.get(ModComponents.FUEL);
		Canister can = stack.get(ModComponents.CANISTER);
		if (fuel != null) {
			each = fuel.radsAtOneMetre(now) + (fuel.type() == FuelType.MOX ? 0.05 : 0.002);
		} else if (stack.getItem() instanceof FuelRodItem rod) {
			each = rod.type() == FuelType.MOX ? 0.05 : 0.002;
		} else if (can != null) {
			each = can.radsAtOneMetre(now);
		} else if (stack.is(ModItems.CORIUM_FRAGMENT)) {
			each = 60;
		} else if (stack.is(ModItems.GRAPHITE_DEBRIS)) {
			each = 4;
		} else if (stack.is(ModItems.PLUTONIUM)) {
			each = 0.02;
		}
		return each * stack.getCount();
	}

	/** Strongest isotope in a stack, for tooltips. */
	public static String format(double rads) {
		if (rads >= 1000) return String.format(java.util.Locale.ROOT, "%.0f krad/s", rads / 1000);
		if (rads >= 10) return String.format(java.util.Locale.ROOT, "%.0f rad/s", rads);
		if (rads >= 0.1) return String.format(java.util.Locale.ROOT, "%.2f rad/s", rads);
		return String.format(java.util.Locale.ROOT, "%.4f rad/s", rads);
	}

	public static String formatTbq(double tbq) {
		if (tbq >= 1000) return String.format(java.util.Locale.ROOT, "%.1f PBq", tbq / 1000);
		if (tbq >= 1) return String.format(java.util.Locale.ROOT, "%.1f TBq", tbq);
		if (tbq >= 0.001) return String.format(java.util.Locale.ROOT, "%.1f GBq", tbq * 1000);
		return String.format(java.util.Locale.ROOT, "%.1f MBq", tbq * 1e6);
	}
}
