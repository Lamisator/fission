package net.antwire.fission.network;

import net.antwire.fission.Fission;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * The radioactive clouds a player can see (up to about a kilometre away, far beyond entity tracking), sent twice a second.
 * An empty list clears them.
 */
public record CloudPayload(List<Cloud> clouds) implements CustomPacketPayload {
	public static final Type<CloudPayload> TYPE = new Type<>(Fission.id("clouds"));
	public static final StreamCodec<FriendlyByteBuf, CloudPayload> CODEC = CustomPacketPayload.codec((p, b) -> {
		b.writeVarInt(p.clouds.size());
		for (Cloud c : p.clouds) {
			b.writeVarInt(c.id);
			b.writeDouble(c.x);
			b.writeDouble(c.y);
			b.writeDouble(c.z);
			b.writeFloat(c.radius);
			b.writeFloat(c.density);
		}
	}, b -> {
		int n = b.readVarInt();
		List<Cloud> list = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			list.add(new Cloud(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat()));
		}
		return new CloudPayload(list);
	});

	public record Cloud(int id, double x, double y, double z, float radius, float density) {
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
