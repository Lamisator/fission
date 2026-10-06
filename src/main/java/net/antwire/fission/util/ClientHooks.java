package net.antwire.fission.util;

import net.antwire.fission.block.entity.SteamTurbineBlockEntity;
import net.minecraft.core.BlockPos;

import java.util.function.Consumer;

/** Client-only actions, filled in by the client entrypoint so common code can trigger them. */
public final class ClientHooks {
	public static Consumer<BlockPos> openReactor = pos -> {
	};
	public static Consumer<SteamTurbineBlockEntity> turbineSound = t -> {
	};

	private ClientHooks() {
	}
}
