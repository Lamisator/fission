package net.antwire.fission.block.entity;

import net.antwire.fission.block.FeedwaterPumpBlock;
import net.antwire.fission.registry.ModBlockEntities;
import net.antwire.gridworks.api.ElectricDevice;
import net.antwire.gridworks.grid.VoltageClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A 10 kV feedwater pump: up to 20 kg/s from the water next to it. It needs 1 kW idle plus the work of pushing water
 * into the reactor: about 9 kW per kg/s against 70 bar, much less while it fills an unpressurised core.
 * Without power it stops - and so does the cooling.
 */
public class FeedwaterPumpBlockEntity extends BlockEntity implements ElectricDevice {
	public static final double MAX_FLOW = 20;
	private double pressure = 1;
	private double flow;
	private double fraction;
	private long suppliedAt = Long.MIN_VALUE;

	public FeedwaterPumpBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.FEEDWATER_PUMP, pos, state);
	}

	public boolean powered() {
		return this.level != null && this.level.getGameTime() - this.suppliedAt < 30 && this.fraction >= 0.2;
	}

	public boolean hasWater() {
		if (this.level == null) {
			return false;
		}
		for (Direction d : Direction.values()) {
			if (this.level.getFluidState(this.worldPosition.relative(d)).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	public double available() {
		// a variable-speed drive: short of power, the pump slows down instead of stopping
		return this.powered() && this.hasWater() && !this.level.hasNeighborSignal(this.worldPosition) ? MAX_FLOW * Math.min(1, this.fraction) : 0;
	}

	public void deliver(double rate, double pressure) {
		this.flow = rate;
		this.pressure = pressure;
		BlockState s = this.getBlockState();
		boolean on = rate > 0.01;
		if (s.getBlock() instanceof FeedwaterPumpBlock && s.getValue(FeedwaterPumpBlock.ON) != on) {
			this.level.setBlock(this.worldPosition, s.setValue(FeedwaterPumpBlock.ON, on), Block.UPDATE_CLIENTS);
		}
	}

	public double flow() {
		return this.flow;
	}

	@Override
	public VoltageClass voltage() {
		return VoltageClass.MV_10KV;
	}

	@Override
	public double demand() {
		return 1000 + Math.max(this.flow, 0.3) * (100 + 130 * this.pressure);
	}

	@Override
	public void supplied(double fraction, long gameTime) {
		this.fraction = fraction;
		this.suppliedAt = gameTime;
	}
}
