package net.antwire.fission.client;

import net.antwire.fission.block.SteamTurbineBlock;
import net.antwire.fission.network.ReactorNetwork;
import net.antwire.fission.registry.ModBlockEntities;
import net.antwire.fission.registry.ModMenus;
import net.antwire.fission.registry.ModSounds;
import net.antwire.fission.util.ClientHooks;
import net.antwire.fission.util.ClientTime;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.antwire.fission.network.CloudPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;

public class FissionClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientTime.now = () -> Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
		ClientHooks.openReactor = pos -> Minecraft.getInstance().gui.setScreen(new ReactorScreen(pos));
		ClientHooks.turbineSound = t -> Minecraft.getInstance().getSoundManager().play(new LoopSound(ModSounds.TURBINE, t, 0.8F,
				() -> t.getBlockState().getBlock() instanceof SteamTurbineBlock && t.getBlockState().getValue(SteamTurbineBlock.SPINNING),
				() -> t.soundPlaying = false));
		ClientPlayNetworking.registerGlobalReceiver(ReactorNetwork.Status.TYPE, (payload, context) -> ReactorScreen.receive(payload));
		MenuScreens.register(ModMenus.STORAGE_1, StorageScreen::new);
		MenuScreens.register(ModMenus.STORAGE_2, StorageScreen::new);
		MenuScreens.register(ModMenus.REPROCESSING, MachineScreen::new);
		MenuScreens.register(ModMenus.CENTRIFUGE, MachineScreen::new);
		BlockEntityRendererRegistry.register(ModBlockEntities.ANNUNCIATOR, PanelRenderer::new);
		BlockEntityRendererRegistry.register(ModBlockEntities.CORE_MAP, PanelRenderer::new);
		BlockEntityRendererRegistry.register(ModBlockEntities.RADIATION_MONITOR, PanelRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(CloudPayload.TYPE, (payload, context) -> context.client().execute(() -> CloudRenderer.receive(payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(CloudRenderer::clear));
		LevelRenderEvents.COLLECT_SUBMITS.register(CloudRenderer::render);
	}
}
