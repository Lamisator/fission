package net.antwire.fission.world;

import net.antwire.fission.block.TubeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Routes one item from a block through the tubes next to it to the nearest block that takes it. */
public final class Tubes {
	private static final int MAX_TUBES = 512;

	private Tubes() {
	}

	/** Sends one item of {@code stack}; true if it went (the caller removes it). */
	public static boolean push(ServerLevel level, BlockPos from, ItemStack stack, TubeKind kind) {
		if (stack.isEmpty() || !kind.carries(stack)) {
			return false;
		}
		ItemStack one = stack.copyWithCount(1);
		Set<Long> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		seen.add(from.asLong());
		for (Direction d : Direction.values()) {
			BlockPos q = from.relative(d);
			if (level.getBlockState(q).getBlock() instanceof TubeBlock t && t.kind() == kind && seen.add(q.asLong())) {
				queue.add(q);
			}
		}
		if (queue.isEmpty()) {
			return false;
		}
		net.minecraft.world.level.block.Block source = level.getBlockState(from).getBlock();
		while (!queue.isEmpty() && seen.size() < MAX_TUBES) {
			BlockPos p = queue.poll();
			for (Direction d : Direction.values()) {
				BlockPos q = p.relative(d);
				if (!seen.add(q.asLong()) || !level.isLoaded(q)) {
					continue;
				}
				if (level.getBlockState(q).getBlock() instanceof TubeBlock t && t.kind() == kind) {
					queue.add(q);
				} else if (level.getBlockEntity(q) instanceof NuclearContainer c && level.getBlockState(q).getBlock() != source
						&& c.acceptsFromTube(one)) {
					c.insertFromTube(one);
					level.playSound(null, p, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.3F, 1.8F);
					level.sendParticles(ParticleTypes.POOF, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.1, 0.1, 0.1, 0.02);
					return true;
				}
			}
		}
		return false;
	}
}
