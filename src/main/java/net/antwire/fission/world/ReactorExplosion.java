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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A reactor blowing up - a prompt critical excursion or a burst pressure vessel. The steam explosion has to go
 * somewhere: every column of blocks above the core must absorb its share of the blast. Columns that cannot - too few
 * blocks, or too weak ones - are thrown into the air, block by block, together with burning, radioactive graphite from
 * the core; then the radioactive inventory gets out. Enough reinforced or heavy concrete (from the Radiation mod) holds
 * the blast in: the lid stays where it is and so does the radiation.
 */
public final class ReactorExplosion {
	private static final int COLUMN_HEIGHT = 24;
	private static final int MAX_FLYING = 600;
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

	/** What a column above a core of {@code fuel} channels must hold for an explosion of severity {@code y} (0..1). */
	public static double required(int fuel, double y) {
		return 30 + 60 * y * Math.sqrt(Math.max(1, fuel));
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
		// which columns hold
		List<BlockPos> fly = new ArrayList<>();
		java.util.Set<Long> failed = new java.util.HashSet<>();
		int held = 0;
		for (int x = core.minX - 1; x <= core.maxX + 1; x++) {
			for (int z = core.minZ - 1; z <= core.maxZ + 1; z++) {
				double sum = 0;
				List<BlockPos> column = new ArrayList<>();
				boolean holds = false;
				for (int dy = 1; dy <= COLUMN_HEIGHT; dy++) {
					BlockPos p = new BlockPos(x, core.maxY + dy, z);
					BlockState s = level.getBlockState(p);
					if (s.isAir()) {
						continue;
					}
					column.add(p);
					sum += strength(s);
					if (sum >= need) {
						holds = true;
						break;
					}
				}
				if (holds) {
					held++;
				} else {
					fly.addAll(column);
					failed.add(BlockPos.asLong(x, 0, z));
				}
			}
		}
		boolean breached = !failed.isEmpty();
		// the core itself: fuel becomes corium, the rest rubble that is thrown out where the lid failed
		List<BlockPos> coriumAt = new ArrayList<>();
		List<BlockPos> debris = new ArrayList<>();
		for (var e : core.roles.entrySet()) {
			BlockPos p = BlockPos.of(e.getKey());
			if (e.getValue() == CoreRole.FUEL) {
				if (level.getBlockEntity(p) instanceof FuelChannelBlockEntity ch && !ch.rod().isEmpty()) {
					coriumAt.add(p);
					ch.clearContent();
				}
			} else if (e.getValue() == CoreRole.GRAPHITE || e.getValue() == CoreRole.ROD || e.getValue() == CoreRole.REFLECTOR) {
				debris.add(p);
			}
			level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		int flying = 0;
		// the lid goes first, top down so the blocks do not hit each other on the way out
		fly.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
		for (BlockPos p : fly) {
			BlockState s = level.getBlockState(p);
			if (s.isAir()) {
				continue;
			}
			if (flying < MAX_FLYING && !s.hasBlockEntity()) {
				launch(level, p, s, center, y, random);
				flying++;
			} else {
				level.destroyBlock(p, false);
			}
		}
		for (BlockPos p : debris) {
			boolean out = failed.contains(BlockPos.asLong(p.getX(), 0, p.getZ()));
			if (out && flying < MAX_FLYING && random.nextFloat() < 0.6F) {
				level.setBlock(p, ModBlocks.REACTOR_DEBRIS.defaultBlockState(), Block.UPDATE_CLIENTS);
				launch(level, p, ModBlocks.REACTOR_DEBRIS.defaultBlockState(), center, y * 1.2, random);
				flying++;
			} else if (!out && random.nextFloat() < 0.4F) {
				level.setBlock(p, ModBlocks.REACTOR_DEBRIS.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		// the blast in the reactor hall
		level.explode(null, center.x, center.y, center.z, (float) (4 + 6 * y), true, Level.ExplosionInteraction.BLOCK);
		for (BlockPos p : coriumAt) {
			if (level.getBlockState(p).isAir() || level.getBlockState(p).canBeReplaced()) {
				Corium.spawn(level, p, 0);
			}
		}
		level.playSound(null, center.x, center.y, center.z, ModSounds.REACTOR_BOOM, SoundSource.BLOCKS, 8.0F, 0.8F);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 1, center.z, 4, 1.5, 1.0, 1.5, 0);
		if (breached) {
			level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, center.x, core.maxY + 2, center.z, 80, 1.5, 4, 1.5, 0.05);
			level.sendParticles(ParticleTypes.FLAME, center.x, core.maxY + 2, center.z, 60, 2, 2, 2, 0.1);
			float rads = (float) (40 * fuel * y);
			float radius = (float) (30 + 4 * Math.sqrt(fuel) * (0.5 + y));
			String name = RadiationApi.addSource(level, "fission_release", center, rads, radius, RadiationApi.Falloff.LINEAR, true);
			Fission.LOGGER.warn("Containment breached ({} of {} columns failed): radiation released as source '{}' ({} rad/s, {} blocks)",
					failed.size(), failed.size() + held, name, rads, radius);
		} else {
			Fission.LOGGER.info("Containment held: all {} columns above the core withstood the blast", held);
		}
	}

	private static void launch(ServerLevel level, BlockPos p, BlockState s, Vec3 center, double y, RandomSource random) {
		FallingBlockEntity e = FallingBlockEntity.fall(level, p, s);
		double dx = p.getX() + 0.5 - center.x;
		double dz = p.getZ() + 0.5 - center.z;
		double len = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
		double h = 0.15 + 0.5 * y * random.nextDouble();
		e.setDeltaMovement(dx / len * h + (random.nextDouble() - 0.5) * 0.3, 0.7 + 1.7 * y * random.nextDouble(),
				dz / len * h + (random.nextDouble() - 0.5) * 0.3);
		e.setHurtsEntities(2.0F, 40);
	}
}
