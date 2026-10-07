package net.antwire.fission.gametest;

import dev.radiation.api.RadiationApi;
import net.antwire.fission.registry.ModBlocks;
import net.antwire.fission.world.Plumes;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;

/**
 * The Kernkraftwerk Funkstadt in the DARC Funkstadt map goes prompt critical: does the hall roof go, how high and far
 * do graphite and fuel fly, and where does the cloud take the radiation? Run with
 * {@code ./gradlew runClientGameTest -Pmap=<unpacked map> -PmapMods=<hamradio,redbutton jars>}.
 */
final class FunkstadtExplosion {
	private static final BlockPos CONTROLLER = new BlockPos(-98, 74, -104);
	private static final Vec3 CORE = new Vec3(-98, 75, -96);
	/** Hall roof: x -119..-77, z -117..-75 at y 101. */
	private static final int RX0 = -119, RX1 = -77, RZ0 = -117, RZ1 = -75, ROOF = 101;
	private static final Vec3 ROUNDABOUT = new Vec3(-224, 70, -320);

	void run(ClientGameTestContext context, Path source) {
		TestWorldSave save;
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			save = sp.getWorldSave();
		}
		copy(source, save.getSaveDirectory());
		try (TestSingleplayerContext sp = save.open()) {
			sp.getConnection().waitForChunksRender();
			for (String c : new String[]{"time set 5000", "weather clear", "gamerule advance_time false", "gamerule advance_weather false",
					"gamemode creative @a", "fission wind set 330 5"}) {
				sp.getServer().runCommand(c);
			}
			context.runOnClient(mc -> mc.options.renderDistance().set(16));
			this.look(context, sp, -40.5, 96, -170.5, new Vec3(-98, 90, -96), 60);
			this.shot(context, "funkstadt_plant_before");
			this.look(context, sp, -210.5, 112, -300.5, new Vec3(-98, 90, -96), 60);
			this.shot(context, "funkstadt_from_city_before");
			System.out.println("[funkstadt] before: " + sp.getServer().computeOnServer(server -> this.roof(server.overworld())) + ", "
					+ sp.getServer().computeOnServer(server -> String.format(Locale.ROOT, "reactor %s",
					server.overworld().getBlockEntity(CONTROLLER) == null ? "missing" : "present")));
			System.out.println("[funkstadt] " + sp.getServer().computeOnServer(server -> net.antwire.fission.world.Wind.describe(server.overworld())));

			// prompt critical
			this.look(context, sp, -40.5, 96, -170.5, new Vec3(-98, 110, -96), 1);
			sp.getServer().runCommand(String.format(Locale.ROOT, "fission excursion %d %d %d", CONTROLLER.getX(), CONTROLLER.getY(), CONTROLLER.getZ()));
			context.waitTicks(8);
			this.shot(context, "funkstadt_explosion");
			double[] top = new double[2];
			for (int i = 0; i < 24; i++) {
				context.waitTicks(10);
				double[] now = sp.getServer().computeOnServer(server -> {
					double h = 0, far = 0;
					for (FallingBlockEntity e : server.overworld().getEntitiesOfClass(FallingBlockEntity.class,
							new AABB(-700, -64, -700, 500, 400, 500))) {
						h = Math.max(h, e.getY() - CORE.y);
						far = Math.max(far, Math.hypot(e.getX() - CORE.x, e.getZ() - CORE.z));
					}
					return new double[]{h, far};
				});
				top[0] = Math.max(top[0], now[0]);
				top[1] = Math.max(top[1], now[1]);
				if (i == 2) {
					this.look(context, sp, -10.5, 110, -210.5, new Vec3(-98, 150, -96), 1);
					this.shot(context, "funkstadt_ejecta");
				}
			}
			System.out.println(String.format(Locale.ROOT, "[funkstadt] ejecta: up to %.0f blocks above the core, %.0f blocks out", top[0], top[1]));
			System.out.println("[funkstadt] after: " + sp.getServer().computeOnServer(server -> this.roof(server.overworld())));
			this.look(context, sp, -40.5, 112, -170.5, new Vec3(-98, 95, -96), 40);
			this.shot(context, "funkstadt_hall_after");
			this.look(context, sp, -98.5, 150, -140.5, new Vec3(-98, 80, -96), 20);
			this.shot(context, "funkstadt_core_from_above");

			// the cloud drifts towards the city
			for (int t = 0; t < 4; t++) {
				this.look(context, sp, -170.5, 80, -330.5, new Vec3(-140, 100, -200), 1);
				context.waitTicks(380);
				for (String line : sp.getServer().computeOnServer(server -> Plumes.describe())) {
					System.out.println("[funkstadt] t=" + (20 * (t + 1) + 12) + " s: " + line);
				}
				System.out.println(String.format(Locale.ROOT, "[funkstadt] t=%d s: %.3f rad/s at the roundabout, %d fallout sources", 20 * (t + 1) + 12,
						this.exposure(sp, ROUNDABOUT.x, ROUNDABOUT.y + 1, ROUNDABOUT.z), this.fallout(sp)));
				this.shot(context, "funkstadt_cloud_" + (t + 1));

			}
			this.look(context, sp, -360.5, 165, -470.5, new Vec3(-170, 95, -230), 30);
			this.shot(context, "funkstadt_cloud_trail");
			this.look(context, sp, -215.5, 72, -312.5, new Vec3(-200, 120, -340), 10);
			this.shot(context, "funkstadt_cloud_overhead");
			System.out.println("[funkstadt] " + sp.getServer().computeOnServer(server -> this.landed(server.overworld())));
			for (double[] p : new double[][]{{-98, 76, -140}, {-60, 70, -96}, {-150, 70, -200}, {-190, 70, -260}, {-224, 70, -320}, {-289, 70, -114},
					{-260, 70, -420}}) {
				System.out.println(String.format(Locale.ROOT, "[funkstadt] dose at %.0f %.0f %.0f (%.0f blocks from the core): %.3f rad/s", p[0], p[1], p[2],
						Math.hypot(p[0] - CORE.x, p[2] - CORE.z), this.exposure(sp, p[0], p[1] + 1, p[2])));
			}
		}
	}

	private String roof(ServerLevel level) {
		int solid = 0, total = 0;
		for (int x = RX0; x <= RX1; x++) {
			for (int z = RZ0; z <= RZ1; z++) {
				total++;
				if (!level.getBlockState(new BlockPos(x, ROOF, z)).isAir()) solid++;
			}
		}
		return String.format(Locale.ROOT, "hall roof %d of %d blocks", solid, total);
	}

	private String landed(ServerLevel level) {
		int graphite = 0, fragments = 0;
		double far = 0, sum = 0;
		for (int x = (int) CORE.x - 450; x <= CORE.x + 450; x++) {
			for (int z = (int) CORE.z - 450; z <= CORE.z + 450; z++) {
				if (!level.hasChunk(x >> 4, z >> 4)) continue;
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
				for (int y = top - 3; y <= top; y++) {
					BlockState s = level.getBlockState(new BlockPos(x, y, z));
					boolean g = s.is(ModBlocks.REACTOR_DEBRIS), f = s.is(ModBlocks.FUEL_FRAGMENT);
					if (g || f) {
						if (g) graphite++; else fragments++;
						double d = Math.hypot(x - CORE.x, z - CORE.z);
						far = Math.max(far, d);
						sum += d;
					}
				}
			}
		}
		int n = graphite + fragments;
		return String.format(Locale.ROOT, "on the ground: %d graphite, %d fuel fragments; mean distance %.0f, farthest %.0f blocks", graphite, fragments,
				n == 0 ? 0 : sum / n, far);
	}

	private int fallout(TestSingleplayerContext sp) {
		return sp.getServer().computeOnServer(server -> (int) dev.radiation.world.RadiationTracker.sources().sources.stream()
				.filter(s -> s.name.startsWith("fallout")).count());
	}

	private float exposure(TestSingleplayerContext sp, double x, double y, double z) {
		return sp.getServer().computeOnServer(server -> RadiationApi.exposureAt(server.overworld(), new Vec3(x, y, z)));
	}

	private void shot(ClientGameTestContext context, String name) {
		context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		context.takeScreenshot(name);
	}

	private void look(ClientGameTestContext context, TestSingleplayerContext sp, double x, double y, double z, Vec3 target, int wait) {
		double dx = target.x - x, dy = target.y - (y + 1.62), dz = target.z - z;
		float yaw = (float) (-Math.toDegrees(Math.atan2(dx, dz)));
		float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
		sp.getServer().runOnServer(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				p.getAbilities().flying = true;
				p.onUpdateAbilities();
				p.setDeltaMovement(Vec3.ZERO);
			}
		});
		context.waitTicks(wait);
	}

	private static void copy(Path source, Path dir) {
		try {
			try (var walk = Files.walk(dir)) {
				walk.sorted(Comparator.reverseOrder()).filter(p -> !p.equals(dir)).forEach(p -> p.toFile().delete());
			}
			try (var walk = Files.walk(source)) {
				for (Path p : (Iterable<Path>) walk::iterator) {
					Path t = dir.resolve(source.relativize(p).toString());
					if (Files.isDirectory(p)) {
						Files.createDirectories(t);
					} else {
						Files.copy(p, t);
					}
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
