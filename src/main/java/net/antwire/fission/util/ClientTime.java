package net.antwire.fission.util;

import java.util.function.LongSupplier;

/** Game time for tooltips on the client (set by the client entrypoint); the server never needs it. */
public final class ClientTime {
	public static LongSupplier now = () -> 0L;

	private ClientTime() {
	}
}
