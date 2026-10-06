package net.antwire.fission.registry;

import net.antwire.fission.Fission;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
	public static final SoundEvent SCRAM = sound("scram");
	public static final SoundEvent ALARM = sound("alarm");
	public static final SoundEvent RAD_ALARM = sound("rad_alarm");
	public static final SoundEvent RELIEF = sound("relief_valve");
	public static final SoundEvent TURBINE = sound("turbine");
	public static final SoundEvent CORIUM = sound("corium");
	public static final SoundEvent STEAM_HISS = sound("steam_hiss");
	public static final SoundEvent REACTOR_BOOM = sound("reactor_boom");

	private ModSounds() {
	}

	public static void init() {
	}

	private static SoundEvent sound(String name) {
		var id = Fission.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
