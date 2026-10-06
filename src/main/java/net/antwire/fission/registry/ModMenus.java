package net.antwire.fission.registry;

import net.antwire.fission.Fission;
import net.antwire.fission.menu.MachineMenu;
import net.antwire.fission.menu.StorageMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
	public static final MenuType<StorageMenu> STORAGE_1 = Registry.register(BuiltInRegistries.MENU, Fission.id("storage_1"),
			new MenuType<>(StorageMenu::oneRow, FeatureFlags.VANILLA_SET));
	public static final MenuType<StorageMenu> STORAGE_2 = Registry.register(BuiltInRegistries.MENU, Fission.id("storage_2"),
			new MenuType<>(StorageMenu::twoRows, FeatureFlags.VANILLA_SET));
	public static final MenuType<MachineMenu> REPROCESSING = Registry.register(BuiltInRegistries.MENU, Fission.id("reprocessing"),
			new MenuType<>(MachineMenu::reprocessing, FeatureFlags.VANILLA_SET));
	public static final MenuType<MachineMenu> CENTRIFUGE = Registry.register(BuiltInRegistries.MENU, Fission.id("centrifuge"),
			new MenuType<>(MachineMenu::centrifuge, FeatureFlags.VANILLA_SET));

	private ModMenus() {
	}

	public static void init() {
	}
}
