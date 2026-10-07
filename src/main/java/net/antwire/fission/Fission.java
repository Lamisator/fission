package net.antwire.fission;

import dev.radiation.api.RadiationApi;
import net.antwire.fission.network.ReactorNetwork;
import net.antwire.fission.nuclear.Dose;
import net.antwire.fission.registry.ModBlockEntities;
import net.antwire.fission.registry.ModBlocks;
import net.antwire.fission.registry.ModComponents;
import net.antwire.fission.registry.ModItems;
import net.antwire.fission.registry.ModMenus;
import net.antwire.fission.registry.ModSounds;
import net.antwire.gridworks.api.GridworksApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.antwire.fission.command.FissionCommands;
import net.antwire.fission.world.Plumes;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.GenerationStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Fission implements ModInitializer {
	public static final String MOD_ID = "fission";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	/** Items are held about half a metre from the body: four times the dose rate at one metre. */
	private static final double CARRY_FACTOR = 4;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModComponents.init();
		ModSounds.init();
		ModBlocks.init();
		ModItems.init();
		ModBlockEntities.init();
		ModMenus.init();
		ReactorNetwork.register();
		GridworksApi.DEVICE.registerForBlockEntity((be, side) -> be, ModBlockEntities.FEEDWATER_PUMP);
		GridworksApi.DEVICE.registerForBlockEntity((be, side) -> be, ModBlockEntities.REPROCESSING);
		GridworksApi.DEVICE.registerForBlockEntity((be, side) -> be, ModBlockEntities.CENTRIFUGE);
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES,
				ResourceKey.create(Registries.PLACED_FEATURE, id("uranium_ore")));
		ServerTickEvents.END_SERVER_TICK.register(Fission::tick);
		ServerTickEvents.END_SERVER_TICK.register(Plumes::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(Plumes::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> Plumes.unload());
		ServerLifecycleEvents.AFTER_SAVE.register((server, flush, force) -> Plumes.save());
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> FissionCommands.register(dispatcher));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ReactorNetwork.forget(handler.getPlayer()));
		LOGGER.info("Fission: reactor physics loaded, {} isotopes tracked.", net.antwire.fission.nuclear.Isotope.values().length);
	}

	/** Once a second: radioactive things in a player's inventory irradiate them (hazmat and Rad-X help, as with any radiation). */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			long now = player.level().getGameTime();
			double rads = 0;
			for (ItemStack stack : player.getInventory()) {
				rads += Dose.radsAtOneMetre(stack, now);
			}
			if (rads > 0.001) {
				RadiationApi.irradiate(player, (float) (rads * CARRY_FACTOR));
			}
		}
	}
}
