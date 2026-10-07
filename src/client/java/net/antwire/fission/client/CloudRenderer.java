package net.antwire.fission.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.antwire.fission.Fission;
import net.antwire.fission.network.CloudPayload;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Radioactive clouds as the server reports them (up to a kilometre away): loose banks of dark grey-brown smoke puffs,
 * always facing the camera, gliding between the twice-a-second updates.
 */
public final class CloudRenderer {
	private static final Identifier TEXTURE = Fission.id("textures/entity/cloud_puff.png");
	private static final RenderType TYPE = RenderTypes.entityTranslucent(TEXTURE);
	private static final int PUFFS = 28;
	private static final long STEP_MS = 500;

	private static final class Seen {
		Vec3 from, to;
		long at;
		float radius, density;
	}

	private static final Map<Integer, Seen> CLOUDS = new HashMap<>();

	private CloudRenderer() {
	}

	public static void receive(CloudPayload payload) {
		long now = System.currentTimeMillis();
		Map<Integer, Seen> next = new HashMap<>();
		for (CloudPayload.Cloud c : payload.clouds()) {
			Seen s = CLOUDS.get(c.id());
			Vec3 to = new Vec3(c.x(), c.y(), c.z());
			if (s == null) {
				s = new Seen();
				s.from = to;
			} else {
				s.from = position(s, now);
			}
			s.to = to;
			s.at = now;
			s.radius = c.radius();
			s.density = c.density();
			next.put(c.id(), s);
		}
		CLOUDS.clear();
		CLOUDS.putAll(next);
		if (Boolean.getBoolean("fission.debugClouds")) System.out.println("[clouds] received " + payload.clouds());
	}

	public static void clear() {
		CLOUDS.clear();
	}

	private static Vec3 position(Seen s, long now) {
		double t = Math.clamp((now - s.at) / (double) STEP_MS, 0, 1);
		return s.from.lerp(s.to, t);
	}

	public static void render(LevelRenderContext context) {
		if (CLOUDS.isEmpty()) {
			return;
		}
		CameraRenderState camera = context.levelState().cameraRenderState;
		if (Boolean.getBoolean("fission.debugClouds") && System.currentTimeMillis() % 2000 < 20) System.out.println("[clouds] render " + CLOUDS.size() + " camera " + camera.pos);
		PoseStack poseStack = context.poseStack();
		long now = System.currentTimeMillis();
		for (Map.Entry<Integer, Seen> e : CLOUDS.entrySet()) {
			Seen s = e.getValue();
			Vec3 at = position(s, now);
			Random r = new Random(e.getKey() * 7919L);
			// drawn somewhat bigger than the part that matters for the dose: the visible smoke reaches further
			float radius = Math.min(Math.max(18, s.radius * 1.3F), 180);
			float alpha = Math.clamp(0.25F + 0.5F * s.density, 0.12F, 0.7F);
			double age = now / 1000.0;
			for (int i = 0; i < PUFFS; i++) {
				double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * radius * 0.75;
				float ox = (float) (Math.cos(a) * d), oz = (float) (Math.sin(a) * d), oy = (float) ((r.nextDouble() - 0.4) * radius * 0.35);
				// the puffs churn slowly
				float wobble = (float) Math.sin(age / 3.0 + i) * radius * 0.04F;
				float size = radius * (0.35F + 0.3F * r.nextFloat());
				int shade = 70 + r.nextInt(40);
				int color = ((int) (alpha * (0.6F + 0.4F * r.nextFloat()) * 255) << 24) | (shade << 16) | ((shade - 6) << 8) | (shade - 12);
				poseStack.pushPose();
				poseStack.translate(at.x + ox + wobble - camera.pos.x, at.y + oy - camera.pos.y, at.z + oz - wobble - camera.pos.z);
				poseStack.rotate(camera.orientation);
				context.submitNodeCollector().submitCustomGeometry(poseStack, TYPE, (pose, b) -> quad(pose, b, size, color));
				poseStack.popPose();
			}
		}
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer b, float s, int color) {
		int light = LightCoordsUtil.FULL_BRIGHT;
		b.addVertex(pose, -s, -s, 0).setColor(color).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
		b.addVertex(pose, s, -s, 0).setColor(color).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
		b.addVertex(pose, s, s, 0).setColor(color).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
		b.addVertex(pose, -s, s, 0).setColor(color).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
	}
}
