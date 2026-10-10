package net.antwire.fission.gametest;

import net.antwire.fission.block.entity.ReactorControllerBlockEntity;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;

/**
 * The Kernkraftwerk Funkstadt as an operator runs it: reset, rods out by hand to about 16 MW, AUTO, then the setpoint
 * doubled twice to 64 MW. Logs the plant every second of game time. Run with
 * {@code ./gradlew runClientGameTest -Pmap=<world> -Pscenes=funkauto -PmapMods=...}.
 */
final class FunkstadtAuto {
	private static final BlockPos CONTROLLER = new BlockPos(-98, 74, -104);
	private static volatile int phase;
	private static volatile long start = -1;
	private static volatile boolean registered;

	void run(ClientGameTestContext context, Path source) {
		TestWorldSave save;
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			save = sp.getWorldSave();
		}
		copy(source, save.getSaveDirectory());
		if (!registered) {
			registered = true;
			ServerTickEvents.END_SERVER_TICK.register(server -> {
				if (phase == 0) return;
				if (!(server.overworld().getBlockEntity(CONTROLLER) instanceof ReactorControllerBlockEntity r)) return;
				long now = server.overworld().getGameTime();
				if (start < 0) start = now;
				long t = now - start;
				this.operate(r, t);
				if (t % 20 == 0) {
					System.out.println(String.format(Locale.ROOT,
							"[auto] t=%4ds ph=%d P=%6.2f Pth=%6.2f MW sp=%5.1f rho=%6.0f pcm per=%7.1f rods=%.4f tgt=%.4f k=%.4f void=%.3f "
									+ "lvl=%.0f%% p=%5.1f bar Tw=%5.1f Tf=%6.1f steam=%5.2f feed=%5.2f out=%5.2f xe=%.0f auto=%b %s",
							t / 20, phase, r.power / 1e6, r.thermalPower() / 1e6, r.setpoint / 1e6, r.rho * 1e5, Math.min(9999, r.period), r.rods,
							r.rodTarget, r.k(), r.voidFraction, r.water / r.capacity() * 100, r.pressure, r.waterTemp, r.fuelTemp, r.steamRate, r.feedRate,
							r.steamOutRate, r.xenonWorth * 1e5, r.auto, r.scram ? "SCRAM " + r.trip : "") + " " + refuel(server.overworld(), r));
				}
			});
		}
		try (TestSingleplayerContext sp = save.open()) {
			sp.getConnection().waitForChunksRender();
			for (String c : new String[]{"gamemode creative @a", "tp @a -98 90 -140", "gamerule advance_weather false"}) {
				sp.getServer().runCommand(c);
			}
			context.waitTicks(40);
			sp.getServer().runOnServer(server -> {
				ReactorControllerBlockEntity r = (ReactorControllerBlockEntity) server.overworld().getBlockEntity(CONTROLLER);
				System.out.println("[auto] loaded: scram=" + r.scram + " " + r.trip + " rods=" + r.rods + " Pth=" + r.thermalPower() + " channels=" + r.channels());
				r.reset();
				r.rods = r.rodTarget = 0.078;
				r.primePower(3e6);
			});
			phase = 1;
			sp.getServer().runCommand("tick sprint 30000");
			for (int i = 0; i < 2000 && phase < 9; i++) {
				context.waitTicks(20);
			}
		}
	}

	/** Phase 1: rods out 1 % at a time until 16 MW, 2: AUTO at 16 MW, 3: setpoint x2, 4: x2 again, 9: done. */
	private long mark;

	private void operate(ReactorControllerBlockEntity r, long t) {
		if (r.scram && phase < 9) {
			System.out.println("[auto] SCRAM at t=" + t / 20 + " s: " + r.trip);
			if (t - this.mark > 1200 || phase == 1) phase = 9;
		}
		switch (phase) {
			case 1 -> {
				if (r.thermalPower() >= 16e6) {
					r.setAuto(true);
					phase = 2;
					this.mark = t;
				} else if (t % 20 == 0 && Math.abs(r.rods - r.rodTarget) < 1e-4) {
					if (r.period > 0 && r.period < 40) r.setRodTarget(r.rods + 0.002);
					else if (r.period > 90 || r.period < 0) r.setRodTarget(r.rods - (r.rho < -0.01 ? 0.05 : 0.002));
				}
			}
			case 2 -> {
				if (t - this.mark > 2400) {
					r.setpoint *= 2;
					phase = 3;
					this.mark = t;
				}
			}
			case 3 -> {
				if (t - this.mark > 3600) {
					r.setpoint *= 2;
					phase = 4;
					this.mark = t;
				}
			}
			case 4 -> {
				if (t - this.mark > 12000) phase = 9;
			}
			default -> {
			}
		}
	}

	/** Channels with a fresh assembly going in, and how many hold spent ones. */
	private static String refuel(net.minecraft.server.level.ServerLevel level, ReactorControllerBlockEntity r) {
		int going = 0, spent = 0;
		for (BlockPos p : r.core.fuel) {
			if (level.getBlockEntity(p) instanceof net.antwire.fission.block.entity.FuelChannelBlockEntity ch) {
				if (ch.refuelling()) going++;
				if (!ch.rod().isEmpty() && net.antwire.fission.item.FuelRodItem.spent(ch.rod())) spent++;
			}
		}
		return "refuel=" + going + " spent=" + spent + String.format(Locale.ROOT, " worth=%.0f", r.core.rodWorth * 1e5);
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
