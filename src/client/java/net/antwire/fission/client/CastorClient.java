package net.antwire.fission.client;

import net.antwire.fission.registry.ModCastor;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.MinecartRenderer;

/** Client side of the CASTOR transport: the wagons look like minecarts carrying their cask or engine; the crane draws itself. */
public final class CastorClient {
	private CastorClient() {
	}

	public static void init() {
		EntityRendererRegistry.register(ModCastor.CASTOR_WAGON, context -> new MinecartRenderer(context, ModelLayers.MINECART));
		EntityRendererRegistry.register(ModCastor.SHUNTING_LOCOMOTIVE, context -> new MinecartRenderer(context, ModelLayers.MINECART));
		BlockEntityRendererRegistry.register(ModCastor.GANTRY_CRANE_BE, GantryCraneRenderer::new);
	}
}
