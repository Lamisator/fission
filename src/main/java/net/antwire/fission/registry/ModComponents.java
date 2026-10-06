package net.antwire.fission.registry;

import net.antwire.fission.Fission;
import net.antwire.fission.nuclear.Canister;
import net.antwire.fission.nuclear.FuelData;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModComponents {
	public static final DataComponentType<FuelData> FUEL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Fission.id("fuel"),
			DataComponentType.<FuelData>builder().persistent(FuelData.CODEC).networkSynchronized(FuelData.STREAM_CODEC).build());
	public static final DataComponentType<Canister> CANISTER = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Fission.id("canister"),
			DataComponentType.<Canister>builder().persistent(Canister.CODEC).networkSynchronized(Canister.STREAM_CODEC).build());

	private ModComponents() {
	}

	public static void init() {
	}
}
