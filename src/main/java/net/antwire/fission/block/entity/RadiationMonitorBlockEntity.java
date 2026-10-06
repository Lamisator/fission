package net.antwire.fission.block.entity;

import dev.radiation.api.RadiationApi;
import net.antwire.fission.block.RadiationMonitorBlock;
import net.antwire.fission.registry.ModBlockEntities;
import net.antwire.fission.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Area radiation monitor: measures the dose rate in front of it every second and alarms above its threshold. */
public class RadiationMonitorBlockEntity extends BlockEntity {
	public static final float[] THRESHOLDS = {0.1F, 1F, 10F, 100F};
	public float rate;
	public int threshold = 1;

	public RadiationMonitorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.RADIATION_MONITOR, pos, state);
	}

	public float limit() {
		return THRESHOLDS[Math.clamp(this.threshold, 0, THRESHOLDS.length - 1)];
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, RadiationMonitorBlockEntity m) {
		if (!(level instanceof ServerLevel sl) || level.getGameTime() % 20 != 0) {
			return;
		}
		var front = net.minecraft.world.phys.Vec3.atCenterOf(pos.relative(state.getValue(RadiationMonitorBlock.FACING)));
		m.rate = RadiationApi.exposureAt(sl, front);
		boolean alarm = m.rate >= m.limit();
		if (state.getValue(RadiationMonitorBlock.ALARM) != alarm) {
			level.setBlock(pos, state.setValue(RadiationMonitorBlock.ALARM, alarm), Block.UPDATE_ALL);
		}
		if (alarm && level.getGameTime() % 40 == 0) {
			level.playSound(null, pos, ModSounds.RAD_ALARM, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
		level.updateNeighbourForOutputSignal(pos, state.getBlock());
		level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.rate = input.getFloatOr("rate", 0);
		this.threshold = input.getIntOr("threshold", 1);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putFloat("rate", this.rate);
		output.putInt("threshold", this.threshold);
	}

	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return this.saveCustomOnly(registries);
	}
}
