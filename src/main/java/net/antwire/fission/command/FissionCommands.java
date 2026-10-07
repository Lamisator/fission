package net.antwire.fission.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.antwire.fission.block.entity.ReactorControllerBlockEntity;
import net.antwire.fission.world.Plumes;
import net.antwire.fission.world.ReactorExplosion;
import net.antwire.fission.world.Wind;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

import java.util.List;

/**
 * {@code /fission wind} - where a radioactive cloud would go ({@code set <towards> <m/s>} fixes it, {@code natural} lets
 * it change again); {@code /fission clouds} - clouds on their way; {@code /fission excursion <controller> [severity]} -
 * makes a reactor go prompt critical, for tests and disaster films.
 */
public final class FissionCommands {
	private FissionCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("fission")
				.then(Commands.literal("wind")
						.executes(FissionCommands::wind)
						.then(Commands.literal("set").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
								.then(Commands.argument("towards", DoubleArgumentType.doubleArg(0, 360))
										.then(Commands.argument("speed", DoubleArgumentType.doubleArg(0, 40))
												.executes(ctx -> {
													Wind.fix(DoubleArgumentType.getDouble(ctx, "towards"), DoubleArgumentType.getDouble(ctx, "speed"));
													Plumes.save();
													return wind(ctx);
												}))))
						.then(Commands.literal("natural").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
								.executes(ctx -> {
									Wind.release();
									Plumes.save();
									return wind(ctx);
								})))
				.then(Commands.literal("clouds").executes(ctx -> {
					List<String> lines = Plumes.describe();
					if (lines.isEmpty()) {
						ctx.getSource().sendSuccess(() -> Component.literal("No radioactive clouds."), false);
					}
					for (String line : lines) {
						ctx.getSource().sendSuccess(() -> Component.literal(line), false);
					}
					return lines.size();
				}))
				.then(Commands.literal("excursion").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
						.then(Commands.argument("controller", BlockPosArgument.blockPos())
								.executes(ctx -> excursion(ctx, 1.0))
								.then(Commands.argument("severity", DoubleArgumentType.doubleArg(0.1, 2.0))
										.executes(ctx -> excursion(ctx, DoubleArgumentType.getDouble(ctx, "severity")))))));
	}

	private static int wind(CommandContext<CommandSourceStack> ctx) {
		String text = Wind.describe(ctx.getSource().getLevel());
		ctx.getSource().sendSuccess(() -> Component.literal(Character.toUpperCase(text.charAt(0)) + text.substring(1)), false);
		return 1;
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
