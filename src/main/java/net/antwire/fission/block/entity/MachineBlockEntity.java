package net.antwire.fission.block.entity;

import dev.radiation.api.RadiationApi;
import net.antwire.fission.menu.MachineMenu;
import net.antwire.fission.nuclear.Dose;
import net.antwire.fission.world.NuclearContainer;
import net.antwire.fission.world.TubeKind;
import net.antwire.fission.world.Tubes;
import net.antwire.gridworks.api.ElectricDevice;
import net.antwire.gridworks.grid.VoltageClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;

/** A 230 V machine with one input slot and a few output slots, running while Gridworks powers it. */
public abstract class MachineBlockEntity extends BaseContainerBlockEntity implements ElectricDevice, NuclearContainer {
	protected NonNullList<ItemStack> items;
	protected final int outputs;
	protected int progress;
	private double fraction;
	private long suppliedAt = Long.MIN_VALUE;
	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int i) {
			return switch (i) {
				case 0 -> MachineBlockEntity.this.progress;
				case 1 -> MachineBlockEntity.this.duration();
				case 2 -> MachineBlockEntity.this.powered() ? 1 : 0;
				default -> 0;
			};
		}

		@Override
		public void set(int i, int value) {
		}

		@Override
		public int getCount() {
			return 3;
		}
	};

	protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int outputs) {
		super(type, pos, state);
		this.outputs = outputs;
		this.items = NonNullList.withSize(1 + outputs, ItemStack.EMPTY);
	}

	protected abstract int duration();

	protected abstract double watts();

	/** Whether the input can be processed now (enough of it, cooled long enough...). */
	protected abstract boolean ready(ItemStack input, long now);

	/** The results for one run; the machine waits until they fit. */
	protected abstract List<ItemStack> results(ItemStack input, long now);

	/** How many input items one run takes. */
	protected abstract int inputCount();

	/** Fraction of the contents' radiation that leaks out of the machine. */
	protected double leak() {
		return 0.05;
	}

	public boolean powered() {
		return this.level != null && this.level.getGameTime() - this.suppliedAt < 30 && this.fraction >= 0.9;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, MachineBlockEntity m) {
		if (!(level instanceof ServerLevel sl)) {
			return;
		}
		long now = level.getGameTime();
		ItemStack in = m.items.getFirst();
		if (!in.isEmpty() && m.ready(in, now) && m.powered()) {
			if (++m.progress >= m.duration()) {
				if (m.finishRun(in)) {
					m.progress = 0;
				} else {
					m.progress = m.duration();
				}
			}
			m.setChanged();
		} else if (m.progress > 0 && (in.isEmpty() || !m.ready(in, now))) {
			m.progress = 0;
		}
		if ((now + pos.asLong()) % 10 == 0) {
			for (int i = 1; i < m.items.size(); i++) {
				ItemStack o = m.items.get(i);
				if (!o.isEmpty() && Tubes.push(sl, pos, o, TubeKind.ISOTOPE)) {
					o.shrink(1);
					m.setChanged();
					break;
				}
			}
			double rads = 0;
			for (ItemStack st : m.items) {
				rads += Dose.radsAtOneMetre(st, now);
			}
			float r = (float) (rads * m.leak());
			RadiationApi.setEmitter(sl, pos, r > 0.005F ? r : 0, RadiationApi.radiusFor(r));
		}
	}

	/** Turns one run of input into output; false if the output does not fit (the machine then waits). */
	protected boolean finishRun(ItemStack in) {
		long now = this.level == null ? 0 : this.level.getGameTime();
		List<ItemStack> out = this.results(in, now);
		if (!this.fits(out)) {
			return false;
		}
		in.shrink(this.inputCount());
		for (ItemStack o : out) {
			this.insertOutput(o);
		}
		return true;
	}

	private boolean fits(List<ItemStack> out) {
		int free = 0;
		for (int i = 1; i < this.items.size(); i++) {
			if (this.items.get(i).isEmpty()) {
				free++;
			}
		}
		return free >= out.size();
	}

	private void insertOutput(ItemStack stack) {
		for (int i = 1; i < this.items.size(); i++) {
			if (this.items.get(i).isEmpty()) {
				this.items.set(i, stack);
				return;
			}
		}
	}

	@Override
	public VoltageClass voltage() {
		return VoltageClass.AC_230V;
	}

	@Override
	public double demand() {
		ItemStack in = this.items.getFirst();
		return !in.isEmpty() && this.level != null && this.ready(in, this.level.getGameTime()) ? this.watts() : 50;
	}

	@Override
	public void supplied(double fraction, long gameTime) {
		this.fraction = fraction;
		this.suppliedAt = gameTime;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == 0 && this.acceptsInput(stack);
	}

	protected abstract boolean acceptsInput(ItemStack stack);

	@Override
	public boolean acceptsFromTube(ItemStack stack) {
		ItemStack in = this.items.getFirst();
		return this.acceptsInput(stack) && (in.isEmpty() || ItemStack.isSameItemSameComponents(in, stack) && in.getCount() < in.getMaxStackSize());
	}

	@Override
	public void insertFromTube(ItemStack one) {
		ItemStack in = this.items.getFirst();
		if (in.isEmpty()) {
			this.items.set(0, one);
		} else {
			in.grow(1);
		}
		this.setChanged();
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	public int getContainerSize() {
		return this.items.size();
	}

	@Override
	protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
		return MachineMenu.create(id, inventory, this, this.data, this.outputs);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(1 + this.outputs, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.items);
		this.progress = input.getIntOr("progress", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items, true);
		output.putInt("progress", this.progress);
	}
}
