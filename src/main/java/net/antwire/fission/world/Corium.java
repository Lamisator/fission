package net.antwire.fission.world;

import dev.radiation.api.RadiationApi;
import net.antwire.fission.block.CoriumBlock;
import net.antwire.fission.registry.ModBlocks;
import net.antwire.fission.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/**
 * Molten corium: fuel, cladding and structure melted together at well over 2000 °C. It sinks, eats down through
 * most materials (concrete only slowly, bedrock never), spreads into a puddle while it is hot, flashes every bit of
 * water near it into steam, sets things on fire, kills anything that touches it and radiates fiercely. Over a quarter
 * of an hour it cools and solidifies into a still deadly lava-like mass - the elephant's foot.
 */
public final class Corium {
	public static final int UPDATE_TICKS = 10;
	private static final TagKey<Block> HEAVY = TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
			net.minecraft.resources.Identifier.fromNamespaceAndPath("radiation", "shielding_heavy"));
	private static final TagKey<Block> CONCRETE = TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
			net.minecraft.resources.Identifier.fromNamespaceAndPath("radiation", "shielding_concrete"));

	private Corium() {
	}

	public static void spawn(ServerLevel level, BlockPos pos, double fuelDose) {
		level.setBlock(pos, ModBlocks.CORIUM.defaultBlockState().setValue(CoriumBlock.HEAT, 15), Block.UPDATE_ALL);
		level.playSound(null, pos, ModSounds.CORIUM, SoundSource.BLOCKS, 2.0F, 0.6F);
	}

	public static float rads(int heat) {
		return 150 + 20 * heat;
	}

	/** Chance per update that the corium melts into the block below it. */
	private static double meltChance(BlockState below) {
		if (below.is(Blocks.BEDROCK) || below.is(Blocks.BARRIER) || below.getBlock().defaultDestroyTime() < 0) {
			return 0;
		}
		if (below.is(HEAVY)) {
			return 1.0 / 900;
		}
		if (below.is(CONCRETE)) {
			var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(below.getBlock());
			return id.getNamespace().equals("radiation") ? 1.0 / 700 : 1.0 / 80;
		}
		return 1.0 / (1 + below.getBlock().defaultDestroyTime() * 3);
	}

	private static boolean passable(BlockState s) {
		return s.isAir() || s.canBeReplaced() || !s.getFluidState().isEmpty() && !s.isSolid();
	}

	public static void update(ServerLevel level, BlockPos pos, BlockState state) {
		int heat = state.getValue(CoriumBlock.HEAT);
		var random = level.getRandom();
		boiling(level, pos);
		burn(level, pos, heat);
		// cooling
		if (random.nextInt(120) == 0) {
			heat--;
			if (heat <= 0) {
				RadiationApi.removeEmitter(level, pos);
				level.setBlock(pos, ModBlocks.SOLID_CORIUM.defaultBlockState(), Block.UPDATE_ALL);
				level.playSound(null, pos, ModSounds.CORIUM, SoundSource.BLOCKS, 0.6F, 0.4F);
				return;
			}
			state = state.setValue(CoriumBlock.HEAT, heat);
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
		}
		RadiationApi.setEmitter(level, pos, rads(heat), RadiationApi.radiusFor(rads(heat)));
		// movement
		BlockPos below = pos.below();
		BlockState b = level.getBlockState(below);
		if (passable(b) && !b.is(ModBlocks.CORIUM)) {
			move(level, pos, below, state);
			return;
		}
		if (heat >= 6 && !b.is(ModBlocks.CORIUM) && !b.is(ModBlocks.SOLID_CORIUM) && random.nextDouble() < meltChance(b)) {
			level.levelEvent(2001, below, Block.getId(b));
			move(level, pos, below, state);
			return;
		}
		if (heat >= 8 && random.nextInt(4) == 0) {
			Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			BlockPos side = pos.relative(d);
			BlockState s = level.getBlockState(side);
			boolean pile = b.is(ModBlocks.CORIUM);
			if (passable(s) && !s.is(ModBlocks.CORIUM) && (pile || passable(level.getBlockState(side.below())))) {
				move(level, pos, side, state);
			}
		}
	}

	private static void move(ServerLevel level, BlockPos from, BlockPos to, BlockState state) {
		RadiationApi.removeEmitter(level, from);
		level.setBlock(to, state, Block.UPDATE_ALL);
		level.setBlock(from, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
	}

	/** Water anywhere near flashes to steam. */
	private static void boiling(ServerLevel level, BlockPos pos) {
		boolean hissed = false;
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 2, 2))) {
			BlockState s = level.getBlockState(p);
			if (s.getFluidState().is(FluidTags.WATER)) {
				if (s.hasProperty(BlockStateProperties.WATERLOGGED)) {
					level.setBlock(p, s.setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_ALL);
				} else if (s.getBlock() == Blocks.WATER || s.canBeReplaced() || !s.isSolid()) {
					level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
				level.sendParticles(ParticleTypes.CLOUD, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
				hissed = true;
			} else if (s.is(BlockTags.ICE) || s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				hissed = true;
			}
		}
		if (hissed) {
			level.playSound(null, pos, ModSounds.STEAM_HISS, SoundSource.BLOCKS, 1.2F, 0.8F + level.getRandom().nextFloat() * 0.3F);
		}
	}

	private static void burn(ServerLevel level, BlockPos pos, int heat) {
		var random = level.getRandom();
		for (Direction d : Direction.values()) {
			BlockPos p = pos.relative(d);
			BlockState s = level.getBlockState(p);
			if (s.isAir() && random.nextInt(6) == 0 && BaseFireBlock.canBePlacedAt(level, p, d)) {
				level.setBlock(p, BaseFireBlock.getState(level, p), Block.UPDATE_ALL);
			} else if ((s.is(Blocks.SAND) || s.is(Blocks.RED_SAND)) && random.nextInt(40) == 0) {
				level.setBlock(p, Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		AABB box = new AABB(pos).inflate(1.0);
		for (Entity e : level.getEntities((Entity) null, box, x -> x instanceof LivingEntity)) {
			LivingEntity le = (LivingEntity) e;
			le.igniteForSeconds(8);
			boolean inside = le.getBoundingBox().intersects(new AABB(pos));
			le.hurtServer(level, level.damageSources().lava(), inside ? 40 : 4 + heat * 0.4F);
		}
		level.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 1, 0.3, 0.0, 0.3, 0);
		if (random.nextInt(3) == 0) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.02);
		}
	}
}
