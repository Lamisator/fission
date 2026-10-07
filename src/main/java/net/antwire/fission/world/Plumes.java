package net.antwire.fission.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.radiation.api.RadiationApi;
import net.antwire.fission.Fission;
import net.antwire.fission.network.CloudPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
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
 * Radioactive clouds. When an explosion opens a core to the sky, the burning core sends up a cloud of volatile fission
 * products: one big puff with the explosion and a few more while the graphite burns. Each puff rises to a few dozen
 * blocks above the ground and drifts with the {@link Wind}, spreading out as it goes. It irradiates the land under it
 * (people indoors are shielded by their roofs) and leaves fallout behind: Radiation-mod sources along its track that
 * fade over days like iodine-131, except for a long-lived part like caesium-137. A cloud fades away once it has spread
 * too thin to matter, typically after a few kilometres.
 */
public final class Plumes {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
	private static final int UPDATE_TICKS = 10;
	/** Radius of a fresh puff and how much it widens per block travelled. */
	private static final double R0 = 10, SPREAD = 0.025;
	/** Fallout is laid down every this many blocks; each deposit takes this fraction of the cloud with it. */
	private static final double DEPOSIT_EVERY = 32, DEPOSIT_TAKES = 0.008;
	/** Deposited dose rate relative to the cloud's dose rate on the ground under it (before spreading out). */
	private static final double DEPOSIT_RATIO = 0.4;
	/** Iodine-131: 8 days; about 15 % of the early fallout dose rate is long-lived caesium. Game time: 1 day = 24000 ticks. */
	private static final long IODINE_HALF_LIFE = 8 * 24000L;
	private static final float LONG_LIVED = 0.15F;
	private static final double FADED = 0.002;
	private static final long MAX_AGE = 30 * 60 * 20;
	private static final int PARTICLE_RANGE = 400;

	static final class Puff {
		String dimension;
		double x, y, z;
		/** Dose rate (rad/s) on the ground under a fresh, undiluted puff. */
		double activity;
		double travelled, sinceDeposit, ground;
		long age;
		String source;
		/** For the clients: an id, the current size and how dense the cloud looks. */
		int id;
		double radius, density;
	}

	static final class Release {
		String dimension;
		double x, y, z;
		double perPuff;
		int puffsLeft;
		long next;
		long smokeUntil;
	}

	static final class State {
		List<Puff> puffs = new ArrayList<>();
		List<Release> releases = new ArrayList<>();
		double windTowards = Double.NaN;
		double windSpeed;
	}

	private static State state = new State();
	private static int nextId;
	/** Players who were last sent at least one cloud (they get an empty list when none are left). */
	private static final java.util.Set<java.util.UUID> SEEING = new java.util.HashSet<>();
	private static final double VISIBLE = 1000;
	private static Path path;

	private Plumes() {
	}

	// ------------------------------------------------------------------ lifecycle

	public static void load(MinecraftServer server) {
		path = server.getWorldPath(LevelResource.ROOT).resolve("fission_plumes.json");
		state = new State();
		if (Files.exists(path)) {
			try (Reader r = Files.newBufferedReader(path)) {
				State s = GSON.fromJson(r, State.class);
				if (s != null) {
					state = s;
				}
			} catch (Exception e) {
				Fission.LOGGER.error("Could not read {}", path, e);
			}
		}
		if (state.puffs == null) state.puffs = new ArrayList<>();
		for (Puff p : state.puffs) {
			p.id = ++nextId;
		}
		if (state.releases == null) state.releases = new ArrayList<>();
		if (Double.isNaN(state.windTowards)) {
			Wind.release();
		} else {
			Wind.fix(state.windTowards, state.windSpeed);
		}
	}

	public static void save() {
		if (path == null) {
			return;
		}
		state.windTowards = Wind.fixed() ? Wind.fixedTowards : Double.NaN;
		state.windSpeed = Wind.fixedSpeed;
		try {
			Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
			try (Writer w = Files.newBufferedWriter(tmp)) {
				GSON.toJson(state, w);
			}
			Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			Fission.LOGGER.error("Could not write {}", path, e);
		}
	}

	public static void unload() {
		save();
		path = null;
		state = new State();
	}

	// ------------------------------------------------------------------ starting a release

	/**
	 * A breached core starts to release: {@code strength} is the cloud's dose rate (rad/s) on the ground right next to
	 * the reactor if all of it went up at once. Half goes with the explosion, the rest in three puffs over a minute.
	 */
	public static void release(ServerLevel level, Vec3 top, double strength) {
		Release r = new Release();
		r.dimension = level.dimension().identifier().toString();
		r.x = top.x;
		r.y = top.y;
		r.z = top.z;
		r.perPuff = strength / 6;
		r.puffsLeft = 3;
		r.next = level.getGameTime() + 400;
		r.smokeUntil = level.getGameTime() + 20 * 120;
		state.releases.add(r);
		puff(level, r.dimension, top, strength / 2);
		Fission.LOGGER.warn("Radioactive cloud released at {} ({} rad/s at the source), {}", top, String.format(Locale.ROOT, "%.2f", strength),
				Wind.describe(level));
		save();
	}

	private static void puff(ServerLevel level, String dimension, Vec3 at, double activity) {
		Puff p = new Puff();
		p.dimension = dimension;
		p.x = at.x;
		p.y = at.y;
		p.z = at.z;
		p.ground = at.y - 6;
		p.activity = activity;
		p.id = ++nextId;
		p.source = RadiationApi.addSource(level, "fission_cloud", at, (float) activity, (float) (R0 + 8), RadiationApi.Falloff.LINEAR, true);
		state.puffs.add(p);
	}

	// ------------------------------------------------------------------ every tick

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % UPDATE_TICKS != 0 || state.puffs.isEmpty() && state.releases.isEmpty() && SEEING.isEmpty()) {
			return;
		}
		boolean changed = false;
		for (Iterator<Release> it = state.releases.iterator(); it.hasNext(); ) {
			Release r = it.next();
			ServerLevel level = level(server, r.dimension);
			if (level == null) {
				continue;
			}
			long now = level.getGameTime();
			if (now < r.smokeUntil) {
				smokeColumn(level, r);
			}
			if (r.puffsLeft > 0 && now >= r.next) {
				puff(level, r.dimension, new Vec3(r.x, r.y, r.z), r.perPuff);
				r.puffsLeft--;
				r.next = now + 400;
				changed = true;
			}
			if (r.puffsLeft <= 0 && now >= r.smokeUntil) {
				it.remove();
				changed = true;
			}
		}
		for (Iterator<Puff> it = state.puffs.iterator(); it.hasNext(); ) {
			Puff p = it.next();
			ServerLevel level = level(server, p.dimension);
			if (level == null) {
				continue;
			}
			if (!drift(level, p)) {
				RadiationApi.removeSource(p.source);
				it.remove();
				changed = true;
			}
		}
		send(server);
		if (changed) {
			save();
		}
	}

	/** Every player gets the clouds within a kilometre. */
	private static void send(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			String dim = player.level().dimension().identifier().toString();
			List<CloudPayload.Cloud> list = new ArrayList<>();
			for (Puff p : state.puffs) {
				if (p.dimension.equals(dim) && p.radius > 0 && player.distanceToSqr(p.x, p.y, p.z) < (VISIBLE + p.radius) * (VISIBLE + p.radius)) {
					list.add(new CloudPayload.Cloud(p.id, p.x, p.y, p.z, (float) p.radius, (float) p.density));
				}
			}
			if (!list.isEmpty() || SEEING.remove(player.getUUID())) {
				ServerPlayNetworking.send(player, new CloudPayload(list));
				if (!list.isEmpty()) {
					SEEING.add(player.getUUID());
				}
			}
		}
	}

	/** Moves a puff one step; false once it has faded away. */
	private static boolean drift(ServerLevel level, Puff p) {
		Vec3 v = Wind.velocity(level).scale(UPDATE_TICKS);
		p.x += v.x;
		p.z += v.z;
		double step = v.horizontalDistance();
		p.travelled += step;
		p.sinceDeposit += step;
		p.age += UPDATE_TICKS;
		int bx = (int) Math.floor(p.x), bz = (int) Math.floor(p.z);
		if (level.hasChunk(bx >> 4, bz >> 4)) {
			p.ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
		}
		// hot gas rises fast, then the cloud settles some dozens of blocks up
		double altitude = 30 + Math.min(30, p.travelled * 0.02);
		double target = p.ground + altitude;
		p.y += Math.clamp((target - p.y) * 0.15, -1.0, 4.0);
		double radius = R0 + SPREAD * p.travelled;
		double onGround = p.activity * (R0 / radius) * (R0 / radius);
		if (onGround < FADED || p.age > MAX_AGE) {
			return false;
		}
		// the cloud as a source: centre in the cloud, reaching the ground with the dose rate under the cloud
		double height = Math.max(0, p.y - p.ground);
		double reach = radius + height + 8;
		double centre = onGround / Math.max(0.05, 1 - height / reach);
		if (!RadiationApi.updateSource(level, p.source, new Vec3(p.x, p.y, p.z), (float) centre, (float) reach)) {
			p.source = RadiationApi.addSource(level, "fission_cloud", new Vec3(p.x, p.y, p.z), (float) centre, (float) reach,
					RadiationApi.Falloff.LINEAR, true);
		}
		if (p.sinceDeposit >= DEPOSIT_EVERY) {
			p.sinceDeposit -= DEPOSIT_EVERY;
			double rads = DEPOSIT_RATIO * onGround * DEPOSIT_EVERY / radius;
			RadiationApi.addSource(level, "fallout", new Vec3(p.x, p.ground + 1, p.z), (float) rads, (float) (radius * 0.9 + 4),
					RadiationApi.Falloff.LINEAR, true, IODINE_HALF_LIFE, LONG_LIVED);
			p.activity *= 1 - DEPOSIT_TAKES;
		}
		p.radius = radius;
		p.density = Math.clamp(Math.sqrt(onGround / 2.0), 0.15, 1.0);
		particles(level, p, radius);
		return true;
	}

	private static void particles(ServerLevel level, Puff p, double radius) {
		RandomSource random = level.getRandom();
		double r = Math.min(radius, 120);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(p.x, p.y, p.z) > (PARTICLE_RANGE + r) * (PARTICLE_RANGE + r)) {
				continue;
			}
			for (int i = 0; i < 24; i++) {
				double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * r;
				double x = p.x + Math.cos(a) * d, z = p.z + Math.sin(a) * d, y = p.y + (random.nextDouble() - 0.5) * r * 0.5;
				level.sendParticles(player, i % 3 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, x, y, z, 1, 0.5, 0.3, 0.5, 0.01);
			}
			// fallout coming down
			for (int i = 0; i < 6; i++) {
				double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * r;
				level.sendParticles(player, ParticleTypes.WHITE_ASH, true, false, p.x + Math.cos(a) * d, p.ground + 2 + random.nextDouble() * 10,
						p.z + Math.sin(a) * d, 4, 2, 2, 2, 0);
			}
		}
	}

	/** The burning core: fire and a black column of smoke while the graphite burns. */
	private static void smokeColumn(ServerLevel level, Release r) {
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(r.x, r.y, r.z) > PARTICLE_RANGE * PARTICLE_RANGE) {
				continue;
			}
			level.sendParticles(player, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, r.x, r.y + 2, r.z, 12, 2.5, 3, 2.5, 0.08);
			level.sendParticles(player, ParticleTypes.LARGE_SMOKE, true, true, r.x, r.y + 6, r.z, 10, 2, 6, 2, 0.05);
			level.sendParticles(player, ParticleTypes.FLAME, true, false, r.x, r.y, r.z, 8, 2.5, 1, 2.5, 0.03);
		}
	}

	private static ServerLevel level(MinecraftServer server, String dimension) {
		Identifier id = Identifier.tryParse(dimension);
		return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
	}

	// ------------------------------------------------------------------ for /fission plumes

	public static List<String> describe() {
		List<String> lines = new ArrayList<>();
		for (Puff p : state.puffs) {
			double radius = R0 + SPREAD * p.travelled;
			lines.add(String.format(Locale.ROOT, "cloud at %.0f %.0f %.0f, %.0f blocks out, %.0f wide, %.3f rad/s below", p.x, p.y, p.z, p.travelled,
					2 * radius, p.activity * (R0 / radius) * (R0 / radius)));
		}
		for (Release r : state.releases) {
			lines.add(String.format(Locale.ROOT, "core at %.0f %.0f %.0f still releasing (%d more puffs)", r.x, r.y, r.z, r.puffsLeft));
		}
		return lines;
	}
}
