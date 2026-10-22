package net.antwire.fission.castor;

import net.antwire.fission.registry.ModCastor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A small diesel shunter. Use it to start it (it drives away from you; sneak to drive towards you), use it again to
 * stop. When it starts it couples every wagon that stands against it, and every wagon against those, at either end:
 * they follow it at a fixed spacing whether it pulls or pushes. It speeds up and brakes gently, runs at most 4 m/s,
 * and stops at a {@link StationRailBlock} until the gantry crane over its wagons is done, the station rail gets a
 * redstone signal, or the station's waiting time has run out (when no crane works on it).
 */
public class ShuntingLocomotive extends AbstractMinecart {
	private static final EntityDataAccessor<Boolean> DATA_RUNNING = SynchedEntityData.defineId(ShuntingLocomotive.class, EntityDataSerializers.BOOLEAN);
	/** Top speed, blocks per tick (4 m/s). */
	public static final double MAX_SPEED = 0.2;
	private static final double ACCEL = 0.004;
	private static final double BRAKE = 0.01;
	private static final double STATION_BRAKE = 0.03;
	/** Distance between the centres of coupled wagons. */
	public static final double SPACING = 1.3;
	private static final double COUPLE_REACH = 1.75;
	private static final int MAX_WAGONS = 16;
	/** A station hold lasts at least this long (ticks), so the crane can notice the train. */
	private static final int MIN_HOLD = 40;

	private Vec3 heading = Vec3.ZERO;
	private double speed;
	private boolean holding;
	private long holdStart;
	/** Set when the train has been released from a station; station rails are ignored until it is off them. */
	private boolean leaving;
	/** Coupled wagons with the vehicle each one follows, nearest first. */
	private final List<Coupling> train = new ArrayList<>();
	/** False after loading from a save until the first tick has looked up the coupled wagons again. */
	private boolean trainLoaded = true;
	private String savedTrain = "";

	private record Coupling(UUID wagon, UUID leader) {
	}

	public ShuntingLocomotive(EntityType<? extends ShuntingLocomotive> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_RUNNING, false);
	}

	public boolean running() {
		return this.entityData.get(DATA_RUNNING);
	}

	private void setRunning(boolean running) {
		this.entityData.set(DATA_RUNNING, running);
	}

	public boolean holding() {
		return this.holding;
	}

	@Override
	public boolean isFurnace() {
		// vanilla carts treat a furnace cart as the one that pushes, not the one that gets pushed
		return true;
	}

	@Override
	public void tick() {
		if (this.level() instanceof ServerLevel sl) {
			this.drive(sl);
		}
		double x0 = this.getX(), z0 = this.getZ();
		super.tick();
		if (this.level() instanceof ServerLevel sl) {
			double dx = this.getX() - x0, dz = this.getZ() - z0;
			double moved = Math.sqrt(dx * dx + dz * dz);
			if (moved > 0.01 && this.speed > 0.01) {
				// follow the track round curves
				this.heading = new Vec3(dx / moved, 0, dz / moved);
			} else if (this.speed > 0.05 && moved < 0.001) {
				// hit the end of the track or something in the way
				this.speed = 0;
			}
			this.pullTrain(sl);
		} else if (this.running() && this.random.nextInt(3) == 0) {
			Vec3 stack = this.position().add(0, 1.15, 0);
			this.level().addParticle(ParticleTypes.SMOKE, stack.x, stack.y, stack.z, 0, 0.05, 0);
		}
	}

	private void drive(ServerLevel level) {
		if (!this.trainLoaded) {
			this.trainLoaded = true;
			this.loadTrain();
		}
		double target = this.running() ? MAX_SPEED : 0;
		BlockPos railPos = this.getCurrentBlockPosOrRailBelow();
		BlockState rail = level.getBlockState(railPos);
		if (!BaseRailBlock.isRail(rail)) {
			// off the end of the track (or derailed): the engine does not drive it through the countryside
			this.speed = 0;
			Vec3 m = this.getDeltaMovement();
			this.setDeltaMovement(m.x * 0.5, m.y, m.z * 0.5);
			return;
		}
		boolean onStation = rail.getBlock() instanceof StationRailBlock;
		if (!onStation) {
			this.leaving = false;
		}
		if (this.running() && onStation && !this.leaving && !this.holding) {
			this.holding = true;
			this.holdStart = level.getGameTime();
		}
		if (this.holding) {
			target = 0;
			long held = level.getGameTime() - this.holdStart;
			if (held >= MIN_HOLD && this.mayLeave(level, railPos, rail, held)) {
				this.holding = false;
				this.leaving = true;
				level.playSound(null, this.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.NEUTRAL, 2.0F, 0.6F);
			}
		}
		double brake = this.holding ? STATION_BRAKE : BRAKE;
		if (this.speed < target) {
			this.speed = Math.min(target, this.speed + ACCEL);
		} else {
			this.speed = Math.max(target, this.speed - brake);
		}
		if (this.heading.lengthSqr() < 0.5) {
			this.speed = 0;
		}
		this.setDeltaMovement(this.heading.scale(this.speed));
	}

	/** At a station: the crane over the train is done, the rail is powered, or (without a crane) the waiting time is up. */
	private boolean mayLeave(ServerLevel level, BlockPos railPos, BlockState rail, long held) {
		if (rail.getBlock() instanceof StationRailBlock && rail.getValue(StationRailBlock.POWERED)) {
			return true;
		}
		boolean crane = false;
		for (Entity e : this.trainEntities(level)) {
			if (e instanceof CastorWagon w) {
				for (GantryCraneBlockEntity c : GantryCraneBlockEntity.covering(level, w)) {
					crane = true;
					if (!c.finishedWith(w)) {
						return false;
					}
				}
			}
		}
		if (crane) {
			return true;
		}
		int wait = rail.getBlock() instanceof StationRailBlock ? StationRailBlock.waitTicks(rail) : 0;
		return held >= wait;
	}

	@Override
	protected Vec3 applyNaturalSlowdown(Vec3 movement) {
		// the engine and the brakes decide the speed (see drive)
		return movement;
	}

	@Override
	protected double getMaxSpeed(ServerLevel level) {
		return MAX_SPEED * 1.25;
	}

	// ------------------------------------------------------------------ the train

	/** The locomotive and every wagon coupled to it that is still there. */
	public List<Entity> trainEntities(ServerLevel level) {
		List<Entity> out = new ArrayList<>();
		out.add(this);
		for (Coupling c : this.train) {
			Entity e = level.getEntity(c.wagon());
			if (e != null) {
				out.add(e);
			}
		}
		return out;
	}

	public int wagons() {
		return this.train.size();
	}

	/** Couples every cart standing against the locomotive, and every cart against those, at either end. */
	public void couple(ServerLevel level) {
		this.train.clear();
		List<AbstractMinecart> members = new ArrayList<>();
		Set<UUID> seen = new HashSet<>();
		members.add(this);
		seen.add(this.getUUID());
		for (int i = 0; i < members.size() && this.train.size() < MAX_WAGONS; i++) {
			AbstractMinecart m = members.get(i);
			AABB box = m.getBoundingBox().inflate(COUPLE_REACH);
			for (AbstractMinecart other : level.getEntitiesOfClass(AbstractMinecart.class, box)) {
				if (seen.contains(other.getUUID()) || other instanceof ShuntingLocomotive || other.distanceTo(m) > COUPLE_REACH
						|| this.train.size() >= MAX_WAGONS) {
					continue;
				}
				seen.add(other.getUUID());
				members.add(other);
				this.train.add(new Coupling(other.getUUID(), m.getUUID()));
			}
		}
	}

	/** Every coupled wagon keeps its distance to the vehicle in front of it (or behind it, when pushed). */
	private void pullTrain(ServerLevel level) {
		this.train.removeIf(c -> {
			Entity w = level.getEntity(c.wagon());
			Entity lead = level.getEntity(c.leader());
			if (!(w instanceof AbstractMinecart wagon) || lead == null || !w.isAlive() || w.distanceTo(lead) > 4) {
				return true;
			}
			double dx = lead.getX() - wagon.getX(), dz = lead.getZ() - wagon.getZ();
			double dist = Math.sqrt(dx * dx + dz * dz);
			if (dist < 1e-4) {
				return false;
			}
			Vec3 u = new Vec3(dx / dist, 0, dz / dist);
			Vec3 leadMove = lead == this ? this.heading.scale(this.speed) : lead.getDeltaMovement();
			double v = leadMove.x * u.x + leadMove.z * u.z + 0.15 * (dist - SPACING);
			if (Math.abs(v) < 0.002 && this.speed == 0) {
				v = 0;
			}
			v = Math.clamp(v, -MAX_SPEED * 1.5, MAX_SPEED * 1.5);
			wagon.setDeltaMovement(u.scale(v));
			return false;
		});
	}

	// ------------------------------------------------------------------ use

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (!(this.level() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		if (this.running()) {
			this.setRunning(false);
			this.holding = false;
			player.sendOverlayMessage(Component.translatable("entity.fission.shunting_locomotive.stopped"));
			return InteractionResult.SUCCESS;
		}
		Vec3 axis = this.railAxis(level);
		if (axis == null) {
			player.sendOverlayMessage(Component.translatable("entity.fission.shunting_locomotive.no_rail"));
			return InteractionResult.SUCCESS;
		}
		Vec3 away = this.position().subtract(player.position());
		double side = away.x * axis.x + away.z * axis.z;
		if (side == 0) {
			side = 1;
		}
		if (player.isShiftKeyDown()) {
			side = -side;
		}
		this.heading = axis.scale(Math.signum(side));
		this.couple(level);
		this.setRunning(true);
		this.holding = false;
		// starting on a station means leaving it
		this.leaving = true;
		level.playSound(null, this.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.NEUTRAL, 2.0F, 0.5F);
		player.sendOverlayMessage(Component.translatable("entity.fission.shunting_locomotive.started", this.train.size(),
				Component.translatable("direction.fission." + net.minecraft.core.Direction.getApproximateNearest(this.heading.x, 0, this.heading.z).getName())));
		return InteractionResult.SUCCESS;
	}

	/** Unit vector along the rail under the locomotive, or null off the rails. */
	private Vec3 railAxis(ServerLevel level) {
		BlockState state = level.getBlockState(this.getCurrentBlockPosOrRailBelow());
		if (!(state.getBlock() instanceof BaseRailBlock rail)) {
			return null;
		}
		RailShape shape = state.getValue(rail.getShapeProperty());
		var exits = AbstractMinecart.exits(shape);
		Vec3i a = exits.getFirst(), b = exits.getSecond();
		Vec3 d = new Vec3(b.getX() - a.getX(), 0, b.getZ() - a.getZ());
		return d.lengthSqr() < 1e-6 ? null : d.normalize();
	}

	/** Starts or stops it from code (tests, commands): {@code direction} is a horizontal vector along the rail. */
	public void start(ServerLevel level, Vec3 direction) {
		Vec3 axis = this.railAxis(level);
		if (axis == null) {
			return;
		}
		double side = direction.x * axis.x + direction.z * axis.z;
		this.heading = axis.scale(side < 0 ? -1 : 1);
		this.couple(level);
		this.setRunning(true);
		this.holding = false;
		this.leaving = true;
	}

	public void stop() {
		this.setRunning(false);
		this.holding = false;
	}

	// ------------------------------------------------------------------ save

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("Running", this.running());
		output.putDouble("HeadingX", this.heading.x);
		output.putDouble("HeadingZ", this.heading.z);
		output.putDouble("Speed", this.speed);
		output.putBoolean("Holding", this.holding);
		output.putLong("HoldStart", this.holdStart);
		output.putBoolean("Leaving", this.leaving);
		StringBuilder b = new StringBuilder();
		for (Coupling c : this.train) {
			b.append(c.wagon()).append('>').append(c.leader()).append(';');
		}
		output.putString("Train", b.toString());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setRunning(input.getBooleanOr("Running", false));
		this.heading = new Vec3(input.getDoubleOr("HeadingX", 0), 0, input.getDoubleOr("HeadingZ", 0));
		this.speed = input.getDoubleOr("Speed", 0);
		this.holding = input.getBooleanOr("Holding", false);
		this.holdStart = input.getLongOr("HoldStart", 0);
		this.leaving = input.getBooleanOr("Leaving", false);
		this.savedTrain = input.getStringOr("Train", "");
		this.trainLoaded = false;
	}

	private void loadTrain() {
		this.train.clear();
		for (String part : this.savedTrain.split(";")) {
			int i = part.indexOf('>');
			if (i > 0) {
				try {
					this.train.add(new Coupling(UUID.fromString(part.substring(0, i)), UUID.fromString(part.substring(i + 1))));
				} catch (IllegalArgumentException ignored) {
					// a damaged entry: that wagon is simply not coupled
				}
			}
		}
	}

	@Override
	protected Item getDropItem() {
		return ModCastor.SHUNTING_LOCOMOTIVE_ITEM;
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(ModCastor.SHUNTING_LOCOMOTIVE_ITEM);
	}

	@Override
	public BlockState getDefaultDisplayBlockState() {
		return ModCastor.SHUNTER_BODY.defaultBlockState();
	}

	@Override
	public int getDefaultDisplayOffset() {
		return 6;
	}

	@Override
	protected Component getTypeName() {
		return Component.translatable("entity.fission.shunting_locomotive");
	}
}
