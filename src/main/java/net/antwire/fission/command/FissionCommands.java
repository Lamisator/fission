package net.antwire.fission.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.antwire.fission.block.entity.ReactorControllerBlockEntity;
import net.antwire.fission.world.ReactorExplosion;
import net.antwire.fission.world.ReactorFires;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

import java.util.List;

/**
 * {@code /fission fires} - burning cores; operators: {@code /fission extinguish [radius]} puts out the fires around
 * them, {@code /fission excursion <controller> [severity]} makes a reactor go prompt critical (tests, disaster films).
 * Wind and clouds are the Radiation mod's: {@code /wind}, {@code /radiation wind}, {@code /radiation clouds}.
 */
public final class FissionCommands {
	private FissionCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("fission")
				.then(Commands.literal("fires").executes(ctx -> {
					List<String> lines = ReactorFires.describe(ctx.getSource().getLevel().getGameTime());
					if (lines.isEmpty()) ctx.getSource().sendSuccess(() -> Component.literal("No reactor is burning."), false);
					for (String line : lines) ctx.getSource().sendSuccess(() -> Component.literal(line), false);
					return lines.size();
				}))
				.then(Commands.literal("extinguish").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
						.executes(ctx -> extinguish(ctx, 128))
						.then(Commands.argument("radius", DoubleArgumentType.doubleArg(1, 100000)).executes(ctx -> extinguish(ctx, DoubleArgumentType.getDouble(ctx, "radius")))))
				.then(Commands.literal("excursion").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
						.then(Commands.argument("controller", BlockPosArgument.blockPos())
								.executes(ctx -> excursion(ctx, 1.0))
								.then(Commands.argument("severity", DoubleArgumentType.doubleArg(0.1, 2.0))
										.executes(ctx -> excursion(ctx, DoubleArgumentType.getDouble(ctx, "severity")))))));
	}

	private static int extinguish(CommandContext<CommandSourceStack> ctx, double radius) {
		int n = ReactorFires.extinguish(ctx.getSource().getLevel(), ctx.getSource().getPosition(), radius);
		ctx.getSource().sendSuccess(() -> Component.literal(n == 0 ? "No reactor burning within " + (int) radius + " blocks" : n + " reactor fire(s) put out"), true);
		return n;
	}

	private static int excursion(CommandContext<CommandSourceStack> ctx, double severity) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "controller");
		var level = ctx.getSource().getLevel();
		if (!(level.getBlockEntity(pos) instanceof ReactorControllerBlockEntity reactor) || reactor.core == null || !reactor.core.valid()) {
			ctx.getSource().sendFailure(Component.literal("No reactor controller with a valid core at " + pos.toShortString()));
			return 0;
		}
		ReactorExplosion.explode(level, reactor, severity, "prompt critical power excursion (by command)");
		ctx.getSource().sendSuccess(() -> Component.literal("Prompt critical excursion at " + pos.toShortString()), true);
		return 1;
	}
}
