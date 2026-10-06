package net.antwire.fission.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.antwire.fission.Fission;
import net.antwire.fission.block.ControlRoomBlock;
import net.antwire.fission.block.RadiationMonitorBlock;
import net.antwire.fission.block.entity.LinkedBlockEntity;
import net.antwire.fission.block.entity.RadiationMonitorBlockEntity;
import net.antwire.fission.block.entity.ReactorControllerBlockEntity;
import net.antwire.fission.reactor.Alarm;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** Front faces of the annunciator panel, the core map and the radiation monitor: lit tiles, coloured cells and digits. */
public class PanelRenderer<T extends BlockEntity> implements BlockEntityRenderer<T, PanelRenderer.State> {
	private static final Identifier TEXTURE = Fission.id("textures/entity/white.png");
	private static final RenderType RENDER_TYPE = RenderTypes.entitySolid(TEXTURE);
	private final Font font;

	public static class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
		int kind;
		int alarms;
		boolean blink;
		int mapW, mapH;
		byte[] map = new byte[0];
		String text = "";
		boolean alarm;
		String[] labels = new String[0];
	}

	public PanelRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.font();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(T be, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
		var bs = be.getBlockState();
		state.facing = bs.hasProperty(ControlRoomBlock.FACING) ? bs.getValue(ControlRoomBlock.FACING) : Direction.NORTH;
		state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
		state.blink = (System.currentTimeMillis() / 400) % 2 == 0;
		if (be instanceof RadiationMonitorBlockEntity m) {
			state.kind = 2;
			state.text = m.rate >= 100 ? String.format(Locale.ROOT, "%.0f", m.rate) : m.rate >= 1 ? String.format(Locale.ROOT, "%.1f", m.rate)
					: String.format(Locale.ROOT, "%.3f", m.rate);
			state.alarm = bs.getValue(RadiationMonitorBlock.ALARM);
			return;
		}
		ReactorControllerBlockEntity c = be instanceof LinkedBlockEntity l ? l.controller() : null;
		state.kind = bs.getBlock() instanceof ControlRoomBlock cr && cr.kind() == ControlRoomBlock.Kind.CORE_MAP ? 1 : 0;
		state.alarms = c == null ? 0 : c.alarms;
		state.mapW = c == null ? 0 : c.mapW;
		state.mapH = c == null ? 0 : c.mapH;
		state.map = c == null ? new byte[0] : c.map;
		if (state.labels.length == 0) {
			state.labels = new String[Alarm.values().length];
			for (Alarm a : Alarm.values()) {
				state.labels[a.ordinal()] = Component.translatable(a.key()).getString();
			}
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.5, 0.5, 0.5);
		poseStack.rotate(Axis.YP.rotationDegrees(-state.facing.toYRot()));
		poseStack.translate(0, 0, 0.502);
		int light = LightCoordsUtil.FULL_BRIGHT;
		switch (state.kind) {
			case 0 -> {
				// 4 x 3 annunciator windows
				collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, b) -> {
					for (Alarm a : Alarm.values()) {
						int i = a.ordinal();
						float x0 = -0.44F + (i % 4) * 0.22F, y0 = 0.40F - (i / 4) * 0.27F;
						boolean on = a.in(state.alarms);
						int c = on ? (a.severe() ? (state.blink ? 0xFFFF3020 : 0xFF901010) : 0xFFFFB020) : 0xFF3A3E44;
						rect(pose, b, x0, y0 - 0.24F, x0 + 0.20F, y0, c, light);
					}
				});
				poseStack.pushPose();
				poseStack.translate(0, 0, 0.006);
				poseStack.scale(1 / 200F, -1 / 200F, 1 / 200F);
				for (Alarm a : Alarm.values()) {
					int i = a.ordinal();
					float cx = (-0.44F + (i % 4) * 0.22F + 0.10F) * 200;
					float cy = -(0.40F - (i / 4) * 0.27F - 0.12F) * 200 - 4;
					String t = state.labels.length > i ? state.labels[i] : "";
					boolean on = a.in(state.alarms);
					String[] words = t.split(" ", 2);
					for (int w = 0; w < words.length; w++) {
						collector.submitText(poseStack, cx - this.font.width(words[w]) / 2F, cy + (w - (words.length - 1) / 2F) * 9, Component.literal(words[w]).getVisualOrderText(),
								false, Font.DisplayMode.POLYGON_OFFSET, light, on ? 0xFF101010 : 0xFF707880, 0, 0);
					}
				}
				poseStack.popPose();
			}
			case 1 -> {
				int w = state.mapW, h = state.mapH;
				collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, b) -> {
					rect(pose, b, -0.45F, -0.45F, 0.45F, 0.45F, 0xFF101418, light);
					if (w > 0 && h > 0) {
						float cell = 0.86F / Math.max(w, h);
						float ox = -cell * w / 2, oy = cell * h / 2;
						for (int i = 0; i < w * h && i < state.map.length; i++) {
							int v = state.map[i];
							if (v < 0) {
								continue;
							}
							float x0 = ox + (i % w) * cell, y1 = oy - (i / w) * cell;
							rect(pose, b, x0 + cell * 0.08F, y1 - cell * 0.92F, x0 + cell * 0.92F, y1 - cell * 0.08F, CoreColors.color(v), light);
						}
					}
				});
			}
			default -> {
				boolean alarm = state.alarm;
				collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, b) -> {
					rect(pose, b, -0.36F, 0.02F, 0.36F, 0.30F, 0xFF102010, light);
					rect(pose, b, -0.30F, -0.34F, -0.10F, -0.14F, alarm && state.blink ? 0xFFFF2020 : 0xFF401010, light);
				});
				poseStack.pushPose();
				poseStack.translate(0, 0, 0.006);
				poseStack.scale(1 / 100F, -1 / 100F, 1 / 100F);
				collector.submitText(poseStack, 34 - this.font.width(state.text), -27, Component.literal(state.text).getVisualOrderText(), false,
						Font.DisplayMode.POLYGON_OFFSET, light, alarm ? 0xFFFF6040 : 0xFF60FF60, 0, 0);
				collector.submitText(poseStack, -2, 18, Component.literal("rad/s").getVisualOrderText(), false, Font.DisplayMode.POLYGON_OFFSET, light,
						0xFF202020, 0, 0);
				poseStack.popPose();
			}
		}
		poseStack.popPose();
	}

	private static void rect(PoseStack.Pose pose, VertexConsumer b, float x0, float y0, float x1, float y1, int color, int light) {
		b.addVertex(pose, x0, y0, 0.002F).setColor(color).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
		b.addVertex(pose, x1, y0, 0.002F).setColor(color).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
		b.addVertex(pose, x1, y1, 0.002F).setColor(color).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
		b.addVertex(pose, x0, y1, 0.002F).setColor(color).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
	}
}
