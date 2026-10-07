package net.antwire.fission.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.radiation.api.RadiationApi;
import net.antwire.fission.Fission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * A blown-up core keeps burning: the graphite glows and burns, as it did for ten days at Chernobyl. While it burns, smoke
 * rises from the core. Under a roof it stays in the building; where the core is open to the sky the smoke rises into it,
 * and every half minute it carries another radioactive cloud away with the wind (Radiation's clouds) - a continuous
 * trail, as long as the fire burns.
 *
 * <p>Putting it out works as it did then: smother it. When nine tenths of the graphite that still burns is
 * covered with something that does not burn - sand, gravel, concrete, water, dumped on top or fallen down the shaft -
 * for half a minute, the fire goes out. Left
 * alone it weakens over days and burns out after ten. {@code /fission extinguish} puts it out at once.
 */
public final class ReactorFires {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int UPDATE_TICKS = 10;
	private static final long PUFF_TICKS = 600;
	private static final long SMOTHER_TICKS = 600;
	private static final long BURN_OUT = 10 * 24000L;
	/** Intensity falls off with this time constant (the graphite burns down), but not below a quarter. */
	private static final double DECAY = 4 * 24000.0;
	/** Each puff carries this share of the cloud strength set at the explosion. */
	private static final double PUFF_SHARE = 0.12;
	private static final int PARTICLE_RANGE = 400;

	static final class Fire {
		String dimension;
		int minX, maxX, minZ, maxZ, top, bottom;
		double strength;
		long started, lastPuff, smothered;
		double open = 1, covered;
	}

	private static List<Fire> fires = new ArrayList<>();
	private static Path path;

	private ReactorFires() {
	}

	public static void load(MinecraftServer server) {
		path = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("fission_fires.json");
		fires = new ArrayList<>();
		if (Files.exists(path)) {
			try (Reader r = Files.newBufferedReader(path)) {
				Fire[] a = GSON.fromJson(r, Fire[].class);
				if (a != null) for (Fire f : a) if (f != null && f.dimension != null) fires.add(f);
			} catch (Exception e) {
				Fission.LOGGER.error("Could not read {}", path, e);
			}
		}
	}

	public static void save() {
		if (path == null) return;
		try (Writer w = Files.newBufferedWriter(path)) {
			GSON.toJson(fires, w);
		} catch (Exception e) {
			Fission.LOGGER.error("Could not write {}", path, e);
		}
	}

	public static void unload() {
		save();
		path = null;
		fires = new ArrayList<>();
	}

	/** A core caught fire: {@code strength} is the cloud strength (rad/s near the core) of the whole release. */
	public static void start(ServerLevel level, int minX, int maxX, int minZ, int maxZ, int bottom, int top, double strength) {
		Fire f = new Fire();
		f.dimension = level.dimension().identifier().toString();
		f.minX = minX;
		f.maxX = maxX;
		f.minZ = minZ;
		f.maxZ = maxZ;
		f.top = top;
		f.bottom = bottom;
		f.strength = strength;
		f.started = level.getGameTime();
		f.lastPuff = level.getGameTime();
		fires.add(f);
		save();
		Fission.LOGGER.warn("The core at {} {} is burning", (minX + maxX) / 2, (minZ + maxZ) / 2);
	}

	public static void tick(MinecraftServer server) {
		if (fires.isEmpty() || server.getTickCount() % UPDATE_TICKS != 0) return;
		boolean changed = false;
		for (Iterator<Fire> it = fires.iterator(); it.hasNext(); ) {
			Fire f = it.next();
			Identifier id = Identifier.tryParse(f.dimension);
			ServerLevel level = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
			if (level == null) continue;
			String out = burn(level, f);
			if (out != null) {
				it.remove();
				changed = true;
				Fission.LOGGER.info("The reactor fire at {} {} is out ({})", (f.minX + f.maxX) / 2, (f.minZ + f.maxZ) / 2, out);
				Vec3 c = centre(f);
				for (ServerPlayer p : level.players()) {
					if (p.distanceToSqr(c) < 256 * 256) p.sendSystemMessage(Component.literal("The reactor fire is out (" + out + ")."));
				}
			}
		}
		if (changed) save();
	}

	/** One second of burning. @return why it went out, or null while it burns */
	private static String burn(ServerLevel level, Fire f) {
		long now = level.getGameTime();
		long age = now - f.started;
		if (age > BURN_OUT) return "burnt out";
		Vec3 c = centre(f);
		if (level.isLoaded(BlockPos.containing(c))) {
			int columns = 0, open = 0, covered = 0, burning = 0;
			BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
			for (int x = f.minX; x <= f.maxX; x++) {
				for (int z = f.minZ; z <= f.maxZ; z++) {
					if (!level.isLoaded(p.set(x, f.top, z))) continue;
					columns++;
					if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) <= f.top + 1) open++;
					// does something still burn in this column, and has something been dumped on top of it (on the core
					// or fallen down the shaft and the crater)?
					boolean onTop = false;
					for (int y = f.top + 3; y >= f.bottom - 12; y--) {
						BlockState s = level.getBlockState(p.set(x, y, z));
						if (burns(s)) {
							burning++;
							if (onTop) covered++;
							break;
						}
						if (smothers(s)) onTop = true;
					}
				}
			}
			if (columns > 0) {
				if (burning == 0) return "nothing left to burn";
				f.open = (double) open / columns;
				f.covered = (double) covered / burning;
			}
		}
		if (f.covered >= 0.9) {
			f.smothered += UPDATE_TICKS;
			if (f.smothered >= SMOTHER_TICKS) return "smothered";
		} else {
			f.smothered = 0;
		}
		double intensity = Math.max(0.25, Math.exp(-age / DECAY)) * (1 - f.covered);
		smoke(level, f, c, intensity);
		if (f.open > 0 && intensity > 0.02 && now - f.lastPuff >= PUFF_TICKS) {
			f.lastPuff = now;
			RadiationApi.releaseCloud(level, new Vec3(c.x, f.top + 2, c.z), f.strength * PUFF_SHARE * f.open * intensity,
					4 + 0.5 * Math.max(f.maxX - f.minX, f.maxZ - f.minZ), 30);
		}
		return null;
	}

	/**
	 * What keeps burning in a wrecked core: the graphite - loose debris, moderator blocks still in place and the channels
	 * set in it. (Molten corium is the other danger of a wrecked core, but not this fire.)
	 */
	private static boolean burns(BlockState s) {
		var b = s.getBlock();
		return b == net.antwire.fission.registry.ModBlocks.REACTOR_DEBRIS || b == net.antwire.fission.registry.ModBlocks.GRAPHITE_MODERATOR
				|| b == net.antwire.fission.registry.ModBlocks.FUEL_CHANNEL;
	}

	/** Anything dumped onto the fire that does not burn itself: sand, gravel, concrete, water... */
	private static boolean smothers(BlockState s) {
		// flames are neither (hot corium keeps lighting them on whatever lies on it), nor is corium, which sand cannot rest on
		var b = s.getBlock();
		return !s.isAir() && !(b instanceof BaseFireBlock) && b != net.antwire.fission.registry.ModBlocks.CORIUM && !burns(s);
	}

	/**
	 * Flames on the core and a column of black smoke above it, widening as it rises: some forty blocks into the sky where
	 * the core is open, up to the roof where there is one (it spreads out under it).
	 */
	private static void smoke(ServerLevel level, Fire f, Vec3 c, double intensity) {
		double w = Math.max(1.5, (f.maxX - f.minX) / 2.0);
		int roof = level.isLoaded(BlockPos.containing(c)) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(c.x), (int) Math.floor(c.z)) : f.top;
		boolean open = f.open > 0 && roof <= f.top + 1;
		double height = open ? 44 : Math.max(3, roof - f.top - 2);
		int per = Math.max(2, (int) Math.round(6 * intensity));
		// the column itself, drawn by the clients (the particles are the flames and the smoke close to the core)
		RadiationApi.smokeColumn(level, "fission_fire_" + f.minX + "_" + f.minZ, new Vec3(c.x, f.top + 1, c.z), w, height, 30);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(c) > PARTICLE_RANGE * PARTICLE_RANGE) continue;
			level.sendParticles(player, ParticleTypes.FLAME, true, false, c.x, f.top + 0.5, c.z, 2 * per, w * 0.6, 0.4, w * 0.6, 0.02);
			level.sendParticles(player, ParticleTypes.LAVA, true, false, c.x, f.top + 0.5, c.z, per, w * 0.6, 0.2, w * 0.6, 0);
			for (double h = 1; h < Math.min(height, 12); h += 3) {
				double spread = w * 0.4 + h * (open ? 0.18 : 0.6);
				level.sendParticles(player, h < 10 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, c.x, f.top + h, c.z, per,
						spread * 0.5, 1.2, spread * 0.5, 0.02);
			}
		}
	}

	private static Vec3 centre(Fire f) {
		return new Vec3((f.minX + f.maxX + 1) / 2.0, f.top + 1, (f.minZ + f.maxZ + 1) / 2.0);
	}

	/** Puts out the fires within {@code radius} of a position. @return how many */
	public static int extinguish(ServerLevel level, Vec3 near, double radius) {
		String dim = level.dimension().identifier().toString();
		int n = 0;
		for (Iterator<Fire> it = fires.iterator(); it.hasNext(); ) {
			Fire f = it.next();
			if (f.dimension.equals(dim) && centre(f).distanceToSqr(near) <= radius * radius) {
				it.remove();
				n++;
			}
		}
		if (n > 0) save();
		return n;
	}

	public static List<String> describe(long now) {
		List<String> out = new ArrayList<>();
		for (Fire f : fires) {
			Vec3 c = centre(f);
			out.add(String.format(Locale.ROOT, "core fire at %.0f %d %.0f, burning %.1f days, %d %% open to the sky, %d %% covered%s", c.x, f.top, c.z,
					(now - f.started) / 24000.0, Math.round(f.open * 100), Math.round(f.covered * 100),
					f.smothered > 0 ? String.format(Locale.ROOT, " (smothering: %d s)", f.smothered / 20) : ""));
		}
		return out;
	}
}
