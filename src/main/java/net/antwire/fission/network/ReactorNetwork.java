package net.antwire.fission.network;

import net.antwire.fission.Fission;
import net.antwire.fission.block.entity.ReactorControllerBlockEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The control panel: the client watches a reactor and sends commands; the server streams its state five times a second. */
public final class ReactorNetwork {
	public static final int CMD_ROD_TARGET = 0, CMD_SCRAM = 1, CMD_RESET = 2, CMD_AUTO = 3, CMD_SETPOINT = 4, CMD_RPS = 5;
	private static final Map<UUID, BlockPos> WATCHERS = new HashMap<>();

	private ReactorNetwork() {
	}

	public record Watch(BlockPos pos, boolean on) implements CustomPacketPayload {
		public static final Type<Watch> TYPE = new Type<>(Fission.id("reactor_watch"));
		public static final StreamCodec<FriendlyByteBuf, Watch> CODEC = CustomPacketPayload.codec((p, b) -> {
			b.writeBlockPos(p.pos);
			b.writeBoolean(p.on);
		}, b -> new Watch(b.readBlockPos(), b.readBoolean()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record Command(BlockPos pos, int cmd, double value) implements CustomPacketPayload {
		public static final Type<Command> TYPE = new Type<>(Fission.id("reactor_command"));
		public static final StreamCodec<FriendlyByteBuf, Command> CODEC = CustomPacketPayload.codec((p, b) -> {
			b.writeBlockPos(p.pos);
			b.writeVarInt(p.cmd);
			b.writeDouble(p.value);
		}, b -> new Command(b.readBlockPos(), b.readVarInt(), b.readDouble()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Index constants for {@link Status#values()}. */
	public static final int V_POWER = 0, V_THERMAL = 1, V_NOMINAL = 2, V_K = 3, V_RHO = 4, V_PERIOD = 5, V_RODS = 6, V_ROD_TARGET = 7,
			V_FUEL_TEMP = 8, V_HOT_TEMP = 9, V_WATER_TEMP = 10, V_LEVEL = 11, V_PRESSURE = 12, V_STEAM = 13, V_FEED = 14, V_STEAM_OUT = 15,
			V_VENT = 16, V_XENON = 17, V_DECAY = 18, V_SCRAM = 19, V_AUTO = 20, V_SETPOINT = 21, V_RPS = 22, V_CHANNELS = 23, V_VOID = 24,
			V_DESTROYED = 25, V_PEAKING = 26, V_COUNT = 27;

	public record Status(BlockPos pos, double[] values, int alarms, String trip, float[] history, byte[] map, int mapW, int mapH)
			implements CustomPacketPayload {
		public static final Type<Status> TYPE = new Type<>(Fission.id("reactor_status"));
		public static final StreamCodec<FriendlyByteBuf, Status> CODEC = CustomPacketPayload.codec((p, b) -> {
			b.writeBlockPos(p.pos);
			b.writeVarInt(p.values.length);
			for (double v : p.values) b.writeDouble(v);
			b.writeVarInt(p.alarms);
			b.writeUtf(p.trip);
			b.writeVarInt(p.history.length);
			for (float v : p.history) b.writeFloat(v);
			b.writeByteArray(p.map);
			b.writeVarInt(p.mapW);
			b.writeVarInt(p.mapH);
		}, b -> {
			BlockPos pos = b.readBlockPos();
			double[] v = new double[b.readVarInt()];
			for (int i = 0; i < v.length; i++) v[i] = b.readDouble();
			int alarms = b.readVarInt();
			String trip = b.readUtf();
			float[] h = new float[b.readVarInt()];
			for (int i = 0; i < h.length; i++) h[i] = b.readFloat();
			return new Status(pos, v, alarms, trip, h, b.readByteArray(), b.readVarInt(), b.readVarInt());
		});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Watch.TYPE, Watch.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Command.TYPE, Command.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Status.TYPE, Status.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Watch.TYPE, (p, ctx) -> {
			if (p.on) {
				WATCHERS.put(ctx.player().getUUID(), p.pos);
			} else {
				WATCHERS.remove(ctx.player().getUUID());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(Command.TYPE, (p, ctx) -> {
			ServerPlayer player = ctx.player();
			if (!(player.level().getBlockEntity(p.pos) instanceof ReactorControllerBlockEntity r)) {
				return;
			}
			switch (p.cmd) {
				case CMD_ROD_TARGET -> r.setRodTarget(p.value);
				case CMD_SCRAM -> r.scram("Manual SCRAM (" + player.getName().getString() + ")");
				case CMD_RESET -> r.reset();
				case CMD_AUTO -> r.setAuto(p.value > 0.5);
				case CMD_SETPOINT -> r.setpoint = Math.clamp(p.value, 100, 1.2 * r.nominal());
				case CMD_RPS -> r.rps = p.value > 0.5;
				default -> {
				}
			}
			r.setChanged();
		});
	}

	public static void forget(ServerPlayer player) {
		WATCHERS.remove(player.getUUID());
	}

	public static void sendStatus(ServerLevel level, ReactorControllerBlockEntity r) {
		if (WATCHERS.isEmpty()) {
			return;
		}
		Status status = null;
		for (Map.Entry<UUID, BlockPos> e : WATCHERS.entrySet()) {
			if (!e.getValue().equals(r.getBlockPos())) {
				continue;
			}
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(e.getKey());
			if (player == null || player.level() != level) {
				continue;
			}
			if (status == null) {
				status = status(r);
			}
			ServerPlayNetworking.send(player, status);
		}
	}

	public static Status status(ReactorControllerBlockEntity r) {
		double[] v = new double[V_COUNT];
		v[V_POWER] = r.power;
		v[V_THERMAL] = r.thermalPower();
		v[V_NOMINAL] = r.nominal();
		v[V_K] = r.k();
		v[V_RHO] = r.rho;
		v[V_PERIOD] = Double.isInfinite(r.period) ? 1e9 : r.period;
		v[V_RODS] = r.rods;
		v[V_ROD_TARGET] = r.rodTarget;
		v[V_FUEL_TEMP] = r.fuelTemp;
		v[V_HOT_TEMP] = r.hotTemp;
		v[V_WATER_TEMP] = r.waterTemp;
		v[V_LEVEL] = r.water / r.capacity();
		v[V_PRESSURE] = r.pressure;
		v[V_STEAM] = r.steamRate;
		v[V_FEED] = r.feedRate;
		v[V_STEAM_OUT] = r.steamOutRate;
		v[V_VENT] = r.ventRate;
		v[V_XENON] = r.xenonWorth;
		v[V_DECAY] = r.decayHeat();
		v[V_SCRAM] = r.scram ? 1 : 0;
		v[V_AUTO] = r.auto ? 1 : 0;
		v[V_SETPOINT] = r.setpoint;
		v[V_RPS] = r.rps ? 1 : 0;
		v[V_CHANNELS] = r.channels();
		v[V_VOID] = r.voidFraction;
		v[V_DESTROYED] = r.destroyed ? 1 : 0;
		v[V_PEAKING] = r.core == null ? 1 : r.core.peaking;
		float[] h = new float[r.history.length];
		for (int i = 0; i < h.length; i++) {
			h[i] = r.history[(r.historyPos + i) % h.length];
		}
		return new Status(r.getBlockPos(), v, r.alarms, r.trip, h, r.map, r.mapW, r.mapH);
	}
}
