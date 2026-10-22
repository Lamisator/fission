package net.antwire.fission.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.antwire.fission.Fission;
import net.antwire.fission.castor.GantryCraneBlock;
import net.antwire.fission.castor.GantryCraneBlockEntity;
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
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import static net.antwire.fission.castor.GantryCraneBlockEntity.HEIGHT;
import static net.antwire.fission.castor.GantryCraneBlockEntity.HOOK_LOAD;
import static net.antwire.fission.castor.GantryCraneBlockEntity.LENGTH;
import static net.antwire.fission.castor.GantryCraneBlockEntity.WIDTH;

/**
 * Draws the gantry crane over its span: the two crane rails on the ground, the gantry (two legs on bogies and the
 * double-girder bridge) at the trolley's position along the crane, the trolley on the bridge, the rope, the hook and,
 * when it carries something, the yellow transfer flask under it.
 */
public class GantryCraneRenderer implements BlockEntityRenderer<GantryCraneBlockEntity, GantryCraneRenderer.State> {
	private static final Identifier TEXTURE = Fission.id("textures/entity/white.png");
	private static final RenderType RENDER_TYPE = RenderTypes.entitySolid(TEXTURE);
	private static final int YELLOW = 0xFFE0B020, YELLOW_D = 0xFFB08818, STEEL = 0xFF5A5E66, DARK = 0xFF2E3034, RAIL = 0xFF6E6A64,
			FLASK = 0xFFE8C030, ROPE = 0xFF1E1E1E, HOOK = 0xFF8A8E96;

	public static class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
		double a, b, h;
		boolean carrying;
		int light;
	}

	public GantryCraneRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(GantryCraneBlockEntity be, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
		state.facing = be.getBlockState().getValue(GantryCraneBlock.FACING);
		double time = be.getLevel() == null ? 0 : be.getLevel().getGameTime() + partialTicks;
		double[] hook = be.hookAt(time);
		state.a = hook[0];
		state.b = hook[1];
		state.h = hook[2];
		state.carrying = !be.carrying().isEmpty();
		state.light = be.getLevel() == null ? LightCoordsUtil.FULL_BRIGHT
				: LightCoordsUtil.getLightCoords(be.getLevel(), be.getBlockPos().above(HEIGHT + 1));
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 128;
	}

	@Override
	public void submit(State s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		Direction f = s.facing, r = f.getClockWise();
		int light = s.light;
		double A = s.a, B = s.b, H = s.h;
		collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, v) -> {
			Box box = new Box(pose, v, f, r, light);
			// crane rails along the whole length, on both sides
			box.draw(1, LENGTH, 0.38, 0.62, 0, 0.14, RAIL);
			box.draw(0, LENGTH, WIDTH - 0.62, WIDTH - 0.38, 0, 0.14, RAIL);
			for (double side : new double[]{0.5, WIDTH - 0.5}) {
				// bogie with wheels, leg, and a knee brace to the bridge
				box.draw(A - 0.65, A + 0.65, side - 0.3, side + 0.3, 0.14, 0.55, DARK);
				box.draw(A - 0.22, A + 0.22, side - 0.22, side + 0.22, 0.55, HEIGHT + 0.6, YELLOW);
				box.draw(A - 0.5, A + 0.5, side - 0.15, side + 0.15, HEIGHT - 0.4, HEIGHT, YELLOW_D);
			}
			// double-girder bridge across the span, with walkway plates on the end carriages
			box.draw(A - 0.48, A - 0.22, 0.25, WIDTH - 0.25, HEIGHT, HEIGHT + 0.62, YELLOW);
			box.draw(A + 0.22, A + 0.48, 0.25, WIDTH - 0.25, HEIGHT, HEIGHT + 0.62, YELLOW);
			box.draw(A - 0.55, A + 0.55, 0.2, 0.8, HEIGHT + 0.62, HEIGHT + 0.72, YELLOW_D);
			box.draw(A - 0.55, A + 0.55, WIDTH - 0.8, WIDTH - 0.2, HEIGHT + 0.62, HEIGHT + 0.72, YELLOW_D);
			// trolley with its hoist drum
			box.draw(A - 0.5, A + 0.5, B - 0.45, B + 0.45, HEIGHT + 0.62, HEIGHT + 0.95, STEEL);
			box.draw(A - 0.18, A + 0.18, B - 0.35, B + 0.35, HEIGHT + 0.95, HEIGHT + 1.2, DARK);
			// two ropes down to the bottom block
			box.draw(A - 0.12, A - 0.08, B - 0.02, B + 0.02, H + 0.3, HEIGHT + 0.62, ROPE);
			box.draw(A + 0.08, A + 0.12, B - 0.02, B + 0.02, H + 0.3, HEIGHT + 0.62, ROPE);
			box.draw(A - 0.2, A + 0.2, B - 0.12, B + 0.12, H + 0.1, H + 0.3, HOOK);
			box.draw(A - 0.05, A + 0.05, B - 0.05, B + 0.05, H, H + 0.1, HOOK);
			if (s.carrying) {
				// the shielded transfer flask round the piece being moved
				box.draw(A - 0.24, A + 0.24, B - 0.24, B + 0.24, H - HOOK_LOAD, H, FLASK);
				box.draw(A - 0.27, A + 0.27, B - 0.27, B + 0.27, H - 0.12, H - 0.04, DARK);
			}
		});
	}

	/** Axis-aligned boxes given in cab coordinates (a along the crane, b to the right, h up), drawn shaded. */
	private record Box(PoseStack.Pose pose, VertexConsumer v, Direction f, Direction r, int light) {
		void draw(double a0, double a1, double b0, double b1, double h0, double h1, int color) {
			double xa0 = x(a0, b0), xa1 = x(a1, b1), za0 = z(a0, b0), za1 = z(a1, b1);
			float x0 = (float) Math.min(xa0, xa1), x1 = (float) Math.max(xa0, xa1);
			float z0 = (float) Math.min(za0, za1), z1 = (float) Math.max(za0, za1);
			float y0 = (float) h0, y1 = (float) h1;
			int top = color, side = shade(color, 0.8F), side2 = shade(color, 0.65F), bottom = shade(color, 0.5F);
			// up, down
			this.quad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, top, 0, 1, 0);
			this.quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, bottom, 0, -1, 0);
			// north (-z), south (+z)
			this.quad(x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, side, 0, 0, -1);
			this.quad(x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, side, 0, 0, 1);
			// west (-x), east (+x)
			this.quad(x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, side2, -1, 0, 0);
			this.quad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, side2, 1, 0, 0);
		}

		private double x(double a, double b) {
			return 0.5 + (a - 0.5) * this.f.getStepX() + (b - 0.5) * this.r.getStepX();
		}

		private double z(double a, double b) {
			return 0.5 + (a - 0.5) * this.f.getStepZ() + (b - 0.5) * this.r.getStepZ();
		}

		private void quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
				int color, float nx, float ny, float nz) {
			this.vertex(ax, ay, az, color, 0, 0, nx, ny, nz);
			this.vertex(bx, by, bz, color, 0, 1, nx, ny, nz);
			this.vertex(cx, cy, cz, color, 1, 1, nx, ny, nz);
			this.vertex(dx, dy, dz, color, 1, 0, nx, ny, nz);
		}

		private void vertex(float x, float y, float z, int color, float u, float w, float nx, float ny, float nz) {
			this.v.addVertex(this.pose, x, y, z).setColor(color).setUv(u, w).setOverlay(OverlayTexture.NO_OVERLAY).setLight(this.light)
					.setNormal(this.pose, nx, ny, nz);
		}

		private static int shade(int argb, float k) {
			int rr = (int) (((argb >> 16) & 0xFF) * k), gg = (int) (((argb >> 8) & 0xFF) * k), bb = (int) ((argb & 0xFF) * k);
			return 0xFF000000 | rr << 16 | gg << 8 | bb;
		}
	}
}
