package net.antwire.fission.castor;

import dev.radiation.api.RadiationApi;
import net.antwire.fission.block.entity.StorageBlockEntities;
import net.antwire.fission.block.entity.StorageBlockEntity;
import net.antwire.fission.nuclear.Dose;
import net.antwire.fission.registry.ModCastor;
import net.antwire.gridworks.api.ElectricDevice;
import net.antwire.gridworks.grid.VoltageClass;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * A gantry crane over a siding: it lifts waste one piece at a time between a CASTOR wagon standing under it and the
 * storage blocks in its span (final repository, long-term storage drums, holding basins). 230 V; it draws 3 kW while
 * it moves (one 16 A cable). The hook travels 1.3 blocks a second sideways and lowers at 2 blocks a second, so a lift takes a few seconds.
 *
 * <p>Positions are kept relative to the cab: {@code a} along the crane (its facing), {@code b} across it (to the right),
 * {@code h} the hook's height above the cab's floor. The server plans one straight move at a time and sends it to the
 * clients, which draw it moving.
 */
public class GantryCraneBlockEntity extends BlockEntity implements ElectricDevice {
	public static final int LENGTH = 9;
	public static final int WIDTH = 7;
	/** Height of the bridge (top of the hook's travel) above the cab's floor. */
	public static final int HEIGHT = 7;
	private static final double TRAVEL_TICKS_PER_BLOCK = 15;
	private static final double HOIST_TICKS_PER_BLOCK = 10;
	private static final double MOVING_WATTS = 3000;
	private static final double IDLE_WATTS = 100;
	/** Lets through what a heavy transfer flask would: the piece on the hook is not naked. */
	private static final double FLASK_LEAK = 1.0e-4;
	/** Height of the transfer flask hanging under the hook. */
	public static final double HOOK_LOAD = 0.6;

	private static final Set<GantryCraneBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

	public enum Mode { OFF, LOAD, UNLOAD }

	enum Phase { IDLE, TO_PICK, LOWER_PICK, RAISE_PICK, TO_DROP, LOWER_DROP, RAISE_DROP, PARK }

	private Mode mode = Mode.UNLOAD;
	private Phase phase = Phase.IDLE;
	private ItemStack carrying = ItemStack.EMPTY;
	/** Where the current job picks up and puts down: a block (storage) or a wagon (by UUID). */
	private @Nullable BlockPos pickBlock, dropBlock;
	private @Nullable UUID pickWagon, dropWagon;
	private int pickSlot = -1;
	// current straight move, in cab coordinates
	public double a0, b0, h0, a1 = 0.5, b1 = 0.5, h1 = HEIGHT - 1;
	public long moveStart;
	public int moveTicks = 1;
	private double fraction;
	private long suppliedAt = Long.MIN_VALUE;
	private long workCheckedAt = Long.MIN_VALUE;
	private boolean workCached;
	private int moved;

	public GantryCraneBlockEntity(BlockPos pos, BlockState state) {
		super(ModCastor.GANTRY_CRANE_BE, pos, state);
		this.a0 = this.a1;
		this.b0 = this.b1;
		this.h0 = this.h1;
	}

	public Mode mode() {
		return this.mode;
	}

	public ItemStack carrying() {
		return this.carrying;
	}

	public boolean busy() {
		return this.phase != Phase.IDLE && this.phase != Phase.PARK;
	}

	public void cycleMode() {
		this.mode = Mode.values()[(this.mode.ordinal() + 1) % Mode.values().length];
		this.workCheckedAt = Long.MIN_VALUE;
		this.setChanged();
		this.sync();
	}

	public void setMode(Mode mode) {
		this.mode = mode;
		this.workCheckedAt = Long.MIN_VALUE;
		this.setChanged();
		this.sync();
	}

	public Direction facing() {
		return this.getBlockState().getValue(GantryCraneBlock.FACING);
	}

	// ------------------------------------------------------------------ geometry

	/** World position of a point given in cab coordinates. */
	public Vec3 world(double a, double b, double h) {
		Direction f = this.facing(), r = f.getClockWise();
		BlockPos p = this.worldPosition;
		// the cab's own block spans [0,1) in a and b; its centre is (0.5, 0.5)
		double ox = p.getX() + 0.5 + (a - 0.5) * f.getStepX() + (b - 0.5) * r.getStepX();
		double oz = p.getZ() + 0.5 + (a - 0.5) * f.getStepZ() + (b - 0.5) * r.getStepZ();
		return new Vec3(ox, p.getY() + h, oz);
	}

	/** Cab coordinates (a, b) of a world position. */
	private double[] local(double x, double z) {
		Direction f = this.facing(), r = f.getClockWise();
		double dx = x - (this.worldPosition.getX() + 0.5), dz = z - (this.worldPosition.getZ() + 0.5);
		return new double[]{dx * f.getStepX() + dz * f.getStepZ() + 0.5, dx * r.getStepX() + dz * r.getStepZ() + 0.5};
	}

	/** Whether a world position lies under the crane (and not far above or below its floor). */
	public boolean spans(Vec3 pos) {
		double[] l = this.local(pos.x, pos.z);
		double dy = pos.y - this.worldPosition.getY();
		return l[0] >= 0 && l[0] < LENGTH && l[1] >= 0 && l[1] < WIDTH && dy >= -2 && dy < HEIGHT - 2;
	}

	private AABB box() {
		Vec3 c0 = this.world(0, 0, -2), c1 = this.world(LENGTH, WIDTH, HEIGHT - 2);
		return new AABB(c0.x, c0.y, c0.z, c1.x, c1.y, c1.z);
	}

	/** Every loaded crane whose span covers the entity. */
	public static List<GantryCraneBlockEntity> covering(ServerLevel level, Entity e) {
		List<GantryCraneBlockEntity> out = new ArrayList<>();
		for (GantryCraneBlockEntity c : List.copyOf(LOADED)) {
			if (c.level == level && !c.isRemoved() && c.spans(e.position())) {
				out.add(c);
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ power

	public boolean powered() {
		return this.level != null && this.level.getGameTime() - this.suppliedAt < 30 && this.fraction >= 0.9;
	}

	@Override
	public VoltageClass voltage() {
		return VoltageClass.AC_230V;
	}

	@Override
	public double demand() {
		return this.busy() ? MOVING_WATTS : IDLE_WATTS;
	}

	@Override
	public void supplied(double fraction, long gameTime) {
		this.fraction = fraction;
		this.suppliedAt = gameTime;
	}

	// ------------------------------------------------------------------ what is under the crane

	private List<CastorWagon> wagons(ServerLevel level) {
		List<CastorWagon> out = new ArrayList<>();
		for (CastorWagon w : level.getEntitiesOfClass(CastorWagon.class, this.box())) {
			if (w.isAlive() && this.spans(w.position()) && w.getDeltaMovement().horizontalDistanceSqr() < 1.0e-4) {
				out.add(w);
			}
		}
		return out;
	}

	/** Storage blocks in the span, the final repository first, then drums, then holding basins. */
	private List<StorageBlockEntity> stores(ServerLevel level) {
		List<StorageBlockEntity> repo = new ArrayList<>(), drums = new ArrayList<>(), basins = new ArrayList<>();
		for (int a = 0; a < LENGTH; a++) {
			for (int b = 0; b < WIDTH; b++) {
				for (int dy = -2; dy < HEIGHT - 2; dy++) {
					Vec3 w = this.world(a + 0.5, b + 0.5, dy + 0.5);
					BlockPos p = BlockPos.containing(w);
					BlockEntity be = level.getBlockEntity(p);
					if (be instanceof FinalRepositoryBlockEntity f) repo.add(f);
					else if (be instanceof StorageBlockEntities.StorageDrum d) drums.add(d);
					else if (be instanceof StorageBlockEntities.HoldingBasin h) basins.add(h);
				}
			}
		}
		repo.addAll(drums);
		repo.addAll(basins);
		return repo;
	}

	private @Nullable CastorWagon wagon(ServerLevel level, @Nullable UUID id) {
		return id != null && level.getEntity(id) instanceof CastorWagon w && w.isAlive() ? w : null;
	}

	/** Whether there is anything left to move for this wagon in the current mode. */
	public boolean hasWork(ServerLevel level, CastorWagon w) {
		long now = level.getGameTime();
		if (this.workCheckedAt == now) {
			return this.workCached;
		}
		this.workCheckedAt = now;
		this.workCached = this.findJob(level, List.of(w), now) != null;
		return this.workCached;
	}

	/**
	 * Whether a train may leave: the crane is switched off, or it has nothing on the hook and either no power or nothing
	 * more to move for this wagon. A piece on the hook keeps the train waiting, power or not.
	 */
	public boolean finishedWith(CastorWagon w) {
		if (!(this.level instanceof ServerLevel sl) || this.mode == Mode.OFF) {
			return true;
		}
		if (this.busy() || !this.carrying.isEmpty()) {
			return false;
		}
		return !this.powered() || !this.hasWork(sl, w);
	}

	private record Job(@Nullable BlockPos pickBlock, @Nullable UUID pickWagon, int slot, @Nullable BlockPos dropBlock, @Nullable UUID dropWagon) {
	}

	private @Nullable Job findJob(ServerLevel level, List<CastorWagon> wagons, long now) {
		if (wagons.isEmpty() || this.mode == Mode.OFF) {
			return null;
		}
		List<StorageBlockEntity> stores = this.stores(level);
		for (CastorWagon w : wagons) {
			if (this.mode == Mode.UNLOAD) {
				for (int i = 0; i < w.getContainerSize(); i++) {
					ItemStack st = w.getItem(i);
					if (st.isEmpty()) continue;
					for (StorageBlockEntity s : stores) {
						if (s.acceptsFromTube(st)) {
							return new Job(null, w.getUUID(), i, s.getBlockPos(), null);
						}
					}
				}
			} else if (w.freeSlot() >= 0) {
				for (StorageBlockEntity s : stores) {
					for (int i = 0; i < s.getContainerSize(); i++) {
						ItemStack st = s.getItem(i);
						if (Permanent.accepts(st, now) && w.accepts(st)) {
							return new Job(s.getBlockPos(), null, i, null, w.getUUID());
						}
					}
				}
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ work

	public static void serverTick(Level level, BlockPos pos, BlockState state, GantryCraneBlockEntity c) {
		if (!(level instanceof ServerLevel sl)) {
			return;
		}
		LOADED.add(c);
		long now = sl.getGameTime();
		if ((now + pos.asLong()) % 20 == 0) {
			float out = (float) (Dose.radsAtOneMetre(c.carrying, now) * FLASK_LEAK);
			RadiationApi.setEmitter(sl, pos, out > 0.005F ? out : 0, RadiationApi.radiusFor(out));
		}
		if (!c.powered()) {
			// a crane without power stands where it is; the move resumes when the power comes back
			if (c.phase != Phase.IDLE && c.phase != Phase.PARK) {
				c.moveStart++;
			}
			return;
		}
		if (c.phase == Phase.IDLE || c.phase == Phase.PARK) {
			if (c.phase == Phase.PARK && now < c.moveStart + c.moveTicks) {
				return;
			}
			if ((now + pos.asLong()) % 10 != 0) {
				return;
			}
			c.plan(sl, now);
			return;
		}
		if (now < c.moveStart + c.moveTicks) {
			return;
		}
		c.next(sl, now);
	}

	private void plan(ServerLevel level, long now) {
		if (!this.carrying.isEmpty()) {
			// a piece still on the hook (its place filled up meanwhile): put it down wherever it fits
			this.deliverAnywhere(level, now);
			return;
		}
		Job job = this.findJob(level, this.wagons(level), now);
		if (job == null) {
			if (this.phase == Phase.IDLE && (Math.abs(this.a1 - 0.5) > 0.01 || Math.abs(this.b1 - 0.5) > 0.01 || this.h1 < HEIGHT - 1.01)) {
				// back to the parking position over the cab
				this.moveTo(Phase.PARK, 0.5, 0.5, HEIGHT - 1, now);
			}
			return;
		}
		this.pickBlock = job.pickBlock();
		this.pickWagon = job.pickWagon();
		this.pickSlot = job.slot();
		this.dropBlock = job.dropBlock();
		this.dropWagon = job.dropWagon();
		double[] at = this.over(level, this.pickBlock, this.pickWagon);
		if (at == null) {
			return;
		}
		this.moveTo(Phase.TO_PICK, at[0], at[1], HEIGHT - 1, now);
		level.playSound(null, this.worldPosition, SoundEvents.MINECART_RIDING, SoundSource.BLOCKS, 0.4F, 0.6F);
	}

	/** Cab coordinates and the hook's height (its underside) over a block or a wagon, with the flask just touching; null if it is gone. */
	private double @Nullable [] over(ServerLevel level, @Nullable BlockPos block, @Nullable UUID wagonId) {
		if (block != null) {
			double[] l = this.local(block.getX() + 0.5, block.getZ() + 0.5);
			return new double[]{l[0], l[1], block.getY() + 1.0 + HOOK_LOAD - this.worldPosition.getY()};
		}
		CastorWagon w = this.wagon(level, wagonId);
		if (w == null) {
			return null;
		}
		double[] l = this.local(w.getX(), w.getZ());
		return new double[]{l[0], l[1], w.getY() + 1.15 + HOOK_LOAD - this.worldPosition.getY()};
	}

	private void next(ServerLevel level, long now) {
		switch (this.phase) {
			case TO_PICK -> {
				double[] at = this.over(level, this.pickBlock, this.pickWagon);
				if (at == null) {
					this.abort(now);
					return;
				}
				this.moveTo(Phase.LOWER_PICK, at[0], at[1], at[2], now);
			}
			case LOWER_PICK -> {
				Container from = this.container(level, this.pickBlock, this.pickWagon);
				ItemStack st = from == null || this.pickSlot < 0 || this.pickSlot >= from.getContainerSize() ? ItemStack.EMPTY : from.getItem(this.pickSlot);
				boolean ok = !st.isEmpty() && (this.pickWagon != null || Permanent.accepts(st, now));
				if (ok) {
					this.carrying = from.removeItem(this.pickSlot, 1);
					from.setChanged();
					level.playSound(null, BlockPos.containing(this.world(this.a1, this.b1, this.h1)), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 0.7F);
				}
				this.moveTo(Phase.RAISE_PICK, this.a1, this.b1, HEIGHT - 1, now);
			}
			case RAISE_PICK -> {
				if (this.carrying.isEmpty()) {
					this.phase = Phase.IDLE;
					this.sync();
					return;
				}
				double[] at = this.over(level, this.dropBlock, this.dropWagon);
				if (at == null) {
					this.deliverAnywhere(level, now);
					return;
				}
				this.moveTo(Phase.TO_DROP, at[0], at[1], HEIGHT - 1, now);
			}
			case TO_DROP -> {
				double[] at = this.over(level, this.dropBlock, this.dropWagon);
				if (at == null) {
					this.deliverAnywhere(level, now);
					return;
				}
				this.moveTo(Phase.LOWER_DROP, at[0], at[1], at[2], now);
			}
			case LOWER_DROP -> {
				if (this.put(level, this.dropBlock, this.dropWagon)) {
					this.carrying = ItemStack.EMPTY;
					this.moved++;
					level.playSound(null, BlockPos.containing(this.world(this.a1, this.b1, this.h1)), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.35F, 0.6F);
				}
				this.moveTo(Phase.RAISE_DROP, this.a1, this.b1, HEIGHT - 1, now);
			}
			case RAISE_DROP -> {
				this.phase = Phase.IDLE;
				this.setChanged();
				this.sync();
			}
			default -> {
			}
		}
	}

	private void abort(long now) {
		this.moveTo(Phase.RAISE_DROP, this.a1, this.b1, HEIGHT - 1, now);
	}

	/** The piece on the hook goes wherever it may: the job's place, another store or wagon, or back where it came from. */
	private void deliverAnywhere(ServerLevel level, long now) {
		BlockPos block = null;
		UUID wagonId = null;
		for (CastorWagon w : this.wagons(level)) {
			if (this.mode == Mode.LOAD && w.accepts(this.carrying)) {
				wagonId = w.getUUID();
				break;
			}
		}
		if (wagonId == null) {
			for (StorageBlockEntity s : this.stores(level)) {
				if (s.acceptsFromTube(this.carrying)) {
					block = s.getBlockPos();
					break;
				}
			}
		}
		if (block == null && wagonId == null) {
			for (CastorWagon w : this.wagons(level)) {
				if (w.accepts(this.carrying)) {
					wagonId = w.getUUID();
					break;
				}
			}
		}
		if (block == null && wagonId == null) {
			// nowhere to put it: wait over the cab and try again
			this.phase = Phase.IDLE;
			return;
		}
		this.dropBlock = block;
		this.dropWagon = wagonId;
		double[] at = this.over(level, block, wagonId);
		this.moveTo(Phase.TO_DROP, at[0], at[1], HEIGHT - 1, now);
	}

	private @Nullable Container container(ServerLevel level, @Nullable BlockPos block, @Nullable UUID wagonId) {
		if (block != null) {
			return level.getBlockEntity(block) instanceof Container c ? c : null;
		}
		return this.wagon(level, wagonId);
	}

	private boolean put(ServerLevel level, @Nullable BlockPos block, @Nullable UUID wagonId) {
		if (block != null) {
			if (level.getBlockEntity(block) instanceof StorageBlockEntity s && s.acceptsFromTube(this.carrying)) {
				s.insertFromTube(this.carrying.copy());
				return true;
			}
			return false;
		}
		CastorWagon w = this.wagon(level, wagonId);
		if (w != null && w.accepts(this.carrying)) {
			w.insert(this.carrying.copy());
			return true;
		}
		return false;
	}

	private void moveTo(Phase phase, double a, double b, double h, long now) {
		// start from where the hook is now (a move may have been cut short)
		double t = this.moveTicks <= 0 ? 1 : Math.clamp((now - this.moveStart) / (double) this.moveTicks, 0, 1);
		this.a0 = this.a0 + (this.a1 - this.a0) * t;
		this.b0 = this.b0 + (this.b1 - this.b0) * t;
		this.h0 = this.h0 + (this.h1 - this.h0) * t;
		this.a1 = a;
		this.b1 = b;
		this.h1 = h;
		double horizontal = Math.max(Math.abs(a - this.a0), Math.abs(b - this.b0));
		double vertical = Math.abs(h - this.h0);
		this.moveTicks = (int) Math.max(6, Math.ceil(horizontal * TRAVEL_TICKS_PER_BLOCK + vertical * HOIST_TICKS_PER_BLOCK));
		this.moveStart = now;
		this.phase = phase;
		this.setChanged();
		this.sync();
	}

	/** Hook position at a moment, in cab coordinates (a, b, h); for the renderer. */
	public double[] hookAt(double time) {
		double t = this.moveTicks <= 0 ? 1 : Math.clamp((time - this.moveStart) / this.moveTicks, 0, 1);
		// ease in and out like a crane drive
		t = t * t * (3 - 2 * t);
		return new double[]{this.a0 + (this.a1 - this.a0) * t, this.b0 + (this.b1 - this.b0) * t, this.h0 + (this.h1 - this.h0) * t};
	}

	public Component status() {
		ServerLevel level = (ServerLevel) this.level;
		int wagons = level == null ? 0 : this.wagons(level).size();
		String what = this.carrying.isEmpty() ? "-" : this.carrying.getHoverName().getString();
		return Component.translatable("block.fission.gantry_crane.status",
				Component.translatable("block.fission.gantry_crane.mode." + this.mode.name().toLowerCase(Locale.ROOT)),
				Component.translatable(this.powered() ? "fission.yes" : "fission.no"), wagons, what, this.moved).withStyle(ChatFormatting.YELLOW);
	}

	private void sync() {
		if (this.level != null && !this.level.isClientSide()) {
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public void setRemoved() {
		LOADED.remove(this);
		super.setRemoved();
	}

	// ------------------------------------------------------------------ save and sync

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("Mode", this.mode.name());
		output.putString("Phase", this.phase.name());
		if (!this.carrying.isEmpty()) {
			output.store("Carrying", ItemStack.OPTIONAL_CODEC, this.carrying);
		}
		if (this.pickBlock != null) output.putLong("PickBlock", this.pickBlock.asLong());
		if (this.dropBlock != null) output.putLong("DropBlock", this.dropBlock.asLong());
		if (this.pickWagon != null) output.putString("PickWagon", this.pickWagon.toString());
		if (this.dropWagon != null) output.putString("DropWagon", this.dropWagon.toString());
		output.putInt("PickSlot", this.pickSlot);
		output.putDouble("A0", this.a0);
		output.putDouble("B0", this.b0);
		output.putDouble("H0", this.h0);
		output.putDouble("A1", this.a1);
		output.putDouble("B1", this.b1);
		output.putDouble("H1", this.h1);
		output.putLong("MoveStart", this.moveStart);
		output.putInt("MoveTicks", this.moveTicks);
		output.putInt("Moved", this.moved);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		try {
			this.mode = Mode.valueOf(input.getStringOr("Mode", "UNLOAD"));
			this.phase = Phase.valueOf(input.getStringOr("Phase", "IDLE"));
		} catch (IllegalArgumentException e) {
			this.mode = Mode.UNLOAD;
			this.phase = Phase.IDLE;
		}
		this.carrying = input.read("Carrying", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		this.pickBlock = input.getLong("PickBlock").map(BlockPos::of).orElse(null);
		this.dropBlock = input.getLong("DropBlock").map(BlockPos::of).orElse(null);
		this.pickWagon = uuid(input.getStringOr("PickWagon", ""));
		this.dropWagon = uuid(input.getStringOr("DropWagon", ""));
		this.pickSlot = input.getIntOr("PickSlot", -1);
		this.a0 = input.getDoubleOr("A0", 0.5);
		this.b0 = input.getDoubleOr("B0", 0.5);
		this.h0 = input.getDoubleOr("H0", HEIGHT - 1);
		this.a1 = input.getDoubleOr("A1", 0.5);
		this.b1 = input.getDoubleOr("B1", 0.5);
		this.h1 = input.getDoubleOr("H1", HEIGHT - 1);
		this.moveStart = input.getLongOr("MoveStart", 0);
		this.moveTicks = input.getIntOr("MoveTicks", 1);
		this.moved = input.getIntOr("Moved", 0);
	}

	private static @Nullable UUID uuid(String s) {
		try {
			return s.isEmpty() ? null : UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
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
