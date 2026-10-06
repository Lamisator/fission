package net.antwire.fission.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.BooleanSupplier;

/** A sound looping at a block entity while a condition holds. */
public class LoopSound extends AbstractTickableSoundInstance {
	private final BlockEntity be;
	private final BooleanSupplier running;
	private final Runnable onStop;

	public LoopSound(SoundEvent event, BlockEntity be, float volume, BooleanSupplier running, Runnable onStop) {
		super(event, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
		this.be = be;
		this.running = running;
		this.onStop = onStop;
		this.looping = true;
		this.delay = 0;
		this.volume = volume;
		this.x = be.getBlockPos().getX() + 0.5;
		this.y = be.getBlockPos().getY() + 0.5;
		this.z = be.getBlockPos().getZ() + 0.5;
	}

	@Override
	public void tick() {
		if (this.be.isRemoved() || !this.running.getAsBoolean()) {
			this.onStop.run();
			this.stop();
		}
	}
}
