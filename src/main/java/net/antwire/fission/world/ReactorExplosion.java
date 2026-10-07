package net.antwire.fission.world;

import dev.radiation.api.RadiationApi;
import net.antwire.fission.Fission;
import net.antwire.fission.block.entity.FuelChannelBlockEntity;
import net.antwire.fission.block.entity.ReactorControllerBlockEntity;
import net.antwire.fission.reactor.CoreModel;
import net.antwire.fission.reactor.CoreRole;
import net.antwire.fission.registry.ModBlocks;
import net.antwire.fission.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A reactor blowing up - a prompt critical excursion or a burst pressure vessel. The steam explosion has to go
 * somewhere: it pushes up and outwards in a cone, and every column of blocks in that cone must absorb its share of the
 * blast, more right above the core, less further to the side. Columns that cannot - too few blocks, or too weak ones -
 * are thrown into the air, block by block, the roof of a reactor hall dozens of blocks up included. Out of the open core
 * fly burning, radioactive graphite and pieces of fuel, hundreds of blocks up and far over the land; and the burning core
 * keeps burning ({@link ReactorFires}), sending radioactive clouds downwind as long as it is open to the sky. Enough reinforced or heavy concrete (from the Radiation mod)
 * holds the blast in: the lid stays where it is and so does the radiation.
 */
public final class ReactorExplosion {
	/** How far up the blast is followed. */
	private static final int SCAN_HEIGHT = 64;
	/** Blocks sideways the blast spreads per block it rises. */
	private static final double CONE = 1.2;
	private static final int MAX_LID_FLYING = 900;
	private static final int MAX_CORE_FLYING = 400;
	private static final TagKey<Block> CONCRETE = TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
			net.minecraft.resources.Identifier.fromNamespaceAndPath("radiation", "shielding_concrete"));

	private ReactorExplosion() {
	}

	/** How much of the blast one block above the core soaks up. */
	public static double strength(BlockState s) {
		if (s.isAir() || !s.getFluidState().isEmpty() && !s.isSolid()) {
			return 0;
		}
		String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
		if (id.equals("radiation:heavy_concrete")) return 140;
		if (id.equals("radiation:reinforced_concrete")) return 120;
		if (s.is(Blocks.OBSIDIAN) || s.is(Blocks.CRYING_OBSIDIAN) || s.is(Blocks.REINFORCED_DEEPSLATE)) return 100;
		if (s.is(Blocks.IRON_BLOCK) || s.is(Blocks.NETHERITE_BLOCK) || s.is(Blocks.GOLD_BLOCK)) return 50;
		if (s.is(CONCRETE)) return 20;
		float r = s.getBlock().getExplosionResistance();
		if (!s.isSolid()) return 0.3;
		return r >= 6 ? 8 : 1 + Math.min(r, 1200) / 10;
	}

	/** What a column right above a core of {@code fuel} channels must hold for an explosion of severity {@code y}. */
	public static double required(int fuel, double y) {
		return 30 + 60 * y * Math.sqrt(Math.max(1, fuel));
	}

	/**
	 * Severity of a prompt critical excursion from the reactivity that drove it: 1.0 just past prompt critical, up to
	 * 1.4 for a big, fast insertion.
	 */
	public static double severity(double rho, double beta) {
		return Math.clamp(1.0 + (rho / beta - 1) * 0.4, 1.0, 1.4);
	}

	/** Share of the blast a column {@code d} blocks to the side of the core must hold. */
	private static double sideways(double d, double y) {
		double w = 5 + 5 * y;
		return 1 / (1 + (d / w) * (d / w));
	}

	public static void explode(ServerLevel level, ReactorControllerBlockEntity reactor, double y, String why) {
		CoreModel core = reactor.core;
		reactor.scram(why);
		reactor.markDestroyed();
		if (core == null || !core.valid()) {
			return;
		}
		RandomSource random = level.getRandom();
		int fuel = core.fuel.size();
		Vec3 center = new Vec3((core.minX + core.maxX + 1) / 2.0, (core.minY + core.maxY + 1) / 2.0, (core.minZ + core.maxZ + 1) / 2.0);
		Fission.LOGGER.warn("Reactor at {} EXPLODED ({}, severity {}), {} fuel channels", reactor.getBlockPos().toShortString(), why, y, fuel);
		double need = required(fuel, y);
		int spread = (int) Math.ceil(SCAN_HEIGHT * CONE);
		// which columns hold: right above the core the full blast, further out less, and only from where the cone reaches
		List<BlockPos> fly = new ArrayList<>();
		Set<Long> failed = new HashSet<>();
		int held = 0, broken = 0, overCore = 0, openOverCore = 0;
		for (int x = core.minX - spread; x <= core.maxX + spread; x++) {
			for (int z = core.minZ - spread; z <= core.maxZ + spread; z++) {
				int dx = Math.max(0, Math.max(core.minX - 1 - x, x - core.maxX - 1));
				int dz = Math.max(0, Math.max(core.minZ - 1 - z, z - core.maxZ - 1));
				double d = Math.sqrt(dx * dx + dz * dz);
				int from = Math.max(1, (int) Math.ceil(d / CONE));
				if (from > SCAN_HEIGHT || !level.isLoaded(new BlockPos(x, core.maxY, z))) {
					continue;
				}
				double pressure = need * sideways(d, y);
				boolean over = d == 0;
				if (over) {
					overCore++;
				}
				double sum = 0;
				List<BlockPos> column = new ArrayList<>();
				boolean holds = false;
				for (int dy = from; dy <= SCAN_HEIGHT; dy++) {
					BlockPos p = new BlockPos(x, core.maxY + dy, z);
					BlockState s = level.getBlockState(p);
					if (s.isAir()) {
						continue;
					}
					column.add(p);
					sum += strength(s);
					if (sum >= pressure) {
						holds = true;
						break;
					}
				}
				if (holds) {
					held++;
				} else {
					fly.addAll(column);
					failed.add(BlockPos.asLong(x, 0, z));
					if (!column.isEmpty()) {
						broken++;
					}
					if (over) {
						openOverCore++;
					}
				}
			}
		}
		double open = overCore == 0 ? 0 : (double) openOverCore / overCore;
		boolean breached = openOverCore > 0;
		Vec3 downwind = RadiationApi.wind(level).normalize();
		// the core itself: most fuel becomes corium; graphite, rods and fuel fragments are thrown out where the lid failed
		List<BlockPos> coriumAt = new ArrayList<>();
		List<BlockPos> debris = new ArrayList<>();
		List<BlockPos> fragments = new ArrayList<>();
		for (var e : core.roles.entrySet()) {
			BlockPos p = BlockPos.of(e.getKey());
			boolean out = failed.contains(BlockPos.asLong(p.getX(), 0, p.getZ()));
			if (e.getValue() == CoreRole.FUEL) {
				if (level.getBlockEntity(p) instanceof FuelChannelBlockEntity ch && !ch.rod().isEmpty()) {
					if (out && random.nextFloat() < 0.35F * y) {
						fragments.add(p);
					} else {
						coriumAt.add(p);
					}
					ch.clearContent();
				}
			} else if (e.getValue() == CoreRole.GRAPHITE || e.getValue() == CoreRole.ROD || e.getValue() == CoreRole.REFLECTOR) {
				debris.add(p);
			}
			level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		// the lid and everything above goes first, top down so the blocks do not hit each other on the way out
		int lidFlying = 0, lidGone = 0;
		fly.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
		for (BlockPos p : fly) {
			BlockState s = level.getBlockState(p);
			if (s.isAir()) {
				continue;
			}
			if (lidFlying < MAX_LID_FLYING && !s.hasBlockEntity() && s.getFluidState().isEmpty() && random.nextFloat() < 0.8F) {
				launchLid(level, p, s, center, y, random);
				lidFlying++;
			} else {
				// shattered: gone in the blast
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				lidGone++;
			}
		}
		int coreFlying = 0;
		for (BlockPos p : fragments) {
			if (coreFlying < MAX_CORE_FLYING) {
				level.setBlock(p, ModBlocks.FUEL_FRAGMENT.defaultBlockState(), Block.UPDATE_CLIENTS);
				launchCore(level, p, ModBlocks.FUEL_FRAGMENT.defaultBlockState(), downwind, y, random);
				coreFlying++;
			} else {
				coriumAt.add(p);
			}
		}
		List<BlockPos> stays = new ArrayList<>();
		for (BlockPos p : debris) {
			boolean out = failed.contains(BlockPos.asLong(p.getX(), 0, p.getZ()));
			if (out && coreFlying < MAX_CORE_FLYING && random.nextFloat() < 0.6F) {
				level.setBlock(p, ModBlocks.REACTOR_DEBRIS.defaultBlockState(), Block.UPDATE_CLIENTS);
				launchCore(level, p, ModBlocks.REACTOR_DEBRIS.defaultBlockState(), downwind, y, random);
				coreFlying++;
			} else if (random.nextFloat() < (out ? 0.3F : 0.4F)) {
				stays.add(p);
			}
		}
		// the blast in the reactor hall
		level.explode(null, center.x, center.y, center.z, (float) (4 + 6 * y), true, Level.ExplosionInteraction.BLOCK);
		// the graphite that stays in the shaft, burning (after the blast, which would otherwise blow it away too)
		for (BlockPos p : stays) {
			BlockPos at = p;
			while (at.getY() > level.getMinY() && level.getBlockState(at.below()).canBeReplaced()) {
				at = at.below();
			}
			if (!level.getBlockState(at).canBeReplaced()) continue;
			level.setBlock(at, ModBlocks.REACTOR_DEBRIS.defaultBlockState(), Block.UPDATE_ALL);
			if (random.nextFloat() < 0.3F && level.getBlockState(at.above()).isAir()) {
				level.setBlock(at.above(), BaseFireBlock.getState(level, at.above()), Block.UPDATE_ALL);
			}
		}
		for (BlockPos p : coriumAt) {
			if (level.getBlockState(p).isAir() || level.getBlockState(p).canBeReplaced()) {
				Corium.spawn(level, p, 0);
			}
		}
		level.playSound(null, center.x, center.y, center.z, ModSounds.REACTOR_BOOM, SoundSource.BLOCKS, 16.0F, 0.7F);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 1, center.z, 6, 2.0, 1.5, 2.0, 0);
		Fission.LOGGER.warn("Blast: {} columns held, {} broken ({} of {} over the core open); {} blocks thrown, {} shattered, {} pieces of the core thrown out",
				held, broken, openOverCore, overCore, lidFlying, lidGone, coreFlying);
		if (breached) {
			Vec3 top = new Vec3(center.x, core.maxY + 1, center.z);
			for (var player : level.players()) {
				if (player.distanceToSqr(center) < 512 * 512) {
					level.sendParticles(player, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, center.x, core.maxY + 4, center.z, 150, 3, 12, 3, 0.1);
					level.sendParticles(player, ParticleTypes.FLAME, true, false, center.x, core.maxY + 2, center.z, 80, 3, 3, 3, 0.15);
					level.sendParticles(player, ParticleTypes.LAVA, true, false, center.x, core.maxY + 2, center.z, 60, 3, 2, 3, 0);
				}
			}
			// what is left of the core keeps radiating from the open shaft (on top of the debris and corium); the short-lived
			// part fades over days
			float rads = (float) (4 * fuel * y * open);
			float radius = (float) (20 + 3 * Math.sqrt(fuel) * (0.5 + y));
			String name = RadiationApi.addSource(level, "fission_release", top, rads, radius, RadiationApi.Falloff.LINEAR, true, 2 * 24000L, 0.3F);
			double strength = 2.2 * y * Math.clamp(Math.sqrt(fuel / 27.0), 0.5, 3.0);
			// half of the volatile inventory goes up with the explosion; the burning core sends the rest after it
			RadiationApi.releaseCloud(level, top, strength * open / 2, 10, 85);
			Fission.LOGGER.warn("Containment breached ({} % of the core open): source '{}' ({} rad/s, {} blocks), cloud {} rad/s",
					Math.round(open * 100), name, rads, radius, String.format(java.util.Locale.ROOT, "%.2f", strength));
		} else {
			Fission.LOGGER.info("Containment held: every column right above the core withstood the blast");
		}
		// the core burns, open or not; under a roof its smoke stays inside
		ReactorFires.start(level, core.minX, core.maxX, core.minZ, core.maxZ, core.minY, core.maxY,
				2.2 * y * Math.clamp(Math.sqrt(fuel / 27.0), 0.5, 3.0));
	}

	/** Roof, lid and whatever stood in the way: up and outwards. */
	private static void launchLid(ServerLevel level, BlockPos p, BlockState s, Vec3 center, double y, RandomSource random) {
		FallingBlockEntity e = FallingBlockEntity.fall(level, p, s);
		e.dropItem = false;
		double dx = p.getX() + 0.5 - center.x;
		double dz = p.getZ() + 0.5 - center.z;
		double len = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
		double h = 0.2 + 1.3 * y * random.nextDouble();
		e.setDeltaMovement(dx / len * h + (random.nextDouble() - 0.5) * 0.4, 1.0 + 3.2 * y * Math.sqrt(random.nextDouble()),
				dz / len * h + (random.nextDouble() - 0.5) * 0.4);
		e.setHurtsEntities(2.0F, 40);
	}

	/** Graphite and fuel: high up and far out, in every direction, more of it downwind. */
	private static void launchCore(ServerLevel level, BlockPos p, BlockState s, Vec3 downwind, double y, RandomSource random) {
		FallingBlockEntity e = FallingBlockEntity.fall(level, p, s);
		double a = random.nextDouble() * Math.PI * 2;
		double hx = Math.cos(a), hz = Math.sin(a);
		if (downwind.lengthSqr() > 0 && random.nextFloat() < 0.35F) {
			hx = downwind.x + (random.nextDouble() - 0.5) * 0.6;
			hz = downwind.z + (random.nextDouble() - 0.5) * 0.6;
		}
		double h = 0.6 + 4.5 * y * random.nextDouble();
		e.setDeltaMovement(hx * h, 2.0 + 4.5 * y * random.nextDouble(), hz * h);
		e.setHurtsEntities(3.0F, 60);
	}
}
