package net.antwire.fission.gametest;

import dev.radiation.api.RadiationApi;
import net.antwire.fission.block.ControlRoomBlock;
import net.antwire.fission.block.SteamTurbineBlock;
import net.antwire.fission.block.TurbineGeneratorBlock;
import net.antwire.fission.block.entity.FuelChannelBlockEntity;
import net.antwire.fission.block.entity.LinkedBlockEntity;
import net.antwire.fission.block.entity.MachineBlockEntity;
import net.antwire.fission.block.entity.ReactorControllerBlockEntity;
import net.antwire.fission.block.entity.SteamTurbineBlockEntity;
import net.antwire.fission.block.entity.StorageBlockEntity;
import net.antwire.fission.client.ReactorScreen;
import net.antwire.fission.nuclear.FuelData;
import net.antwire.fission.nuclear.FuelType;
import net.antwire.fission.registry.ModBlocks;
import net.antwire.fission.registry.ModComponents;
import net.antwire.fission.registry.ModItems;
import net.antwire.gridworks.block.ConverterBlock;
import net.antwire.gridworks.block.GeneratorBlock;
import net.antwire.gridworks.block.LampBlock;
import net.antwire.gridworks.block.entity.GeneratorBlockEntity;
import net.antwire.gridworks.grid.ConductorType;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.function.Consumer;

/** Builds reactors in a flat world, runs them, breaks them, and photographs everything. */
public class ReactorTour implements FabricClientGameTest {
	private static final int[][] RODS = {{1, 0}, {1, 4}, {3, 0}, {3, 4}, {0, 1}, {4, 1}, {0, 3}, {4, 3}};
	private int g;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1280, 720);
		try (TestSingleplayerContext sp = context.worldBuilder()
				.setUseConsistentSettings(true)
				.adjustSettings(s -> s.getNormalPresetList().stream().filter(e -> e.preset() != null && e.preset().is(WorldPresets.FLAT)).findFirst()
						.ifPresent(s::setWorldType))
				.create()) {
			sp.getConnection().waitForChunksRender();
			for (String c : new String[]{"time set 6000", "weather clear", "gamerule advance_time false", "gamerule advance_weather false",
					"gamemode creative @a", "gamerule spawn_mobs false"}) {
				sp.getServer().runCommand(c);
			}
			this.g = sp.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				int y = level.getMinY() + 4;
				while (!level.getBlockState(new BlockPos(0, y, 0)).isAir()) y++;
				return y;
			});
			String scenes = System.getProperty("fission.scenes", "all");
			if (scenes.equals("all") || scenes.contains("plant")) this.plant(context, sp);
			if (scenes.equals("all") || scenes.contains("fuel")) this.fuelCycle(context, sp);
			if (scenes.equals("all") || scenes.contains("meltdown")) this.meltdown(context, sp);
			if (scenes.equals("all") || scenes.contains("explosion")) this.explosions(context, sp);
		}
	}

	// ------------------------------------------------------------------ helpers

	private static void set(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_ALL);
	}

	private static void cable(ServerLevel level, BlockPos pos, ConductorType type) {
		level.setBlock(pos, Block.updateFromNeighbourShapes(net.antwire.gridworks.registry.ModBlocks.CABLES.get(type).defaultBlockState(), level, pos),
				Block.UPDATE_ALL);
	}

	private static void pipe(ServerLevel level, BlockPos pos, Block block) {
		level.setBlock(pos, Block.updateFromNeighbourShapes(block.defaultBlockState(), level, pos), Block.UPDATE_ALL);
	}

	/** A 3x3x3 LEU core (fuel at pitch 2 in graphite, eight rods, beryllium shell) with its south-west corner at x, z. */
	private static BlockPos core(ServerLevel level, int x, int y, int z, boolean fuel) {
		for (int dx = -1; dx <= 5; dx++) {
			for (int dz = -1; dz <= 5; dz++) {
				for (int dy = 0; dy <= 4; dy++) {
					boolean edge = dx == -1 || dx == 5 || dz == -1 || dz == 5 || dy == 0 || dy == 4;
					set(level, new BlockPos(x + dx, y + dy, z + dz), (edge ? ModBlocks.BERYLLIUM_REFLECTOR : ModBlocks.GRAPHITE_MODERATOR).defaultBlockState());
				}
			}
		}
		for (int i = 0; i < 3; i++) {
			for (int k = 0; k < 3; k++) {
				for (int dy = 1; dy <= 3; dy++) {
					BlockPos p = new BlockPos(x + 2 * i, y + dy, z + 2 * k);
					set(level, p, ModBlocks.FUEL_CHANNEL.defaultBlockState());
					if (fuel && level.getBlockEntity(p) instanceof FuelChannelBlockEntity ch) {
						ch.setRod(new ItemStack(ModItems.LEU_FUEL_ROD));
					}
				}
			}
		}
		for (int[] r : RODS) {
			for (int dy = 1; dy <= 3; dy++) {
				set(level, new BlockPos(x + r[0], y + dy, z + r[1]), ModBlocks.CONTROL_ROD.defaultBlockState());
			}
		}
		set(level, new BlockPos(x - 1, y, z + 2), ModBlocks.FEEDWATER_INLET.defaultBlockState());
		set(level, new BlockPos(x + 5, y + 2, z + 2), ModBlocks.STEAM_OUTLET.defaultBlockState());
		set(level, new BlockPos(x + 2, y + 4, z + 2), ModBlocks.RELIEF_VALVE.defaultBlockState());
		BlockPos controller = new BlockPos(x + 2, y + 2, z - 2);
		set(level, controller, ModBlocks.REACTOR_CONTROLLER.defaultBlockState().setValue(net.antwire.fission.block.ReactorControllerBlock.FACING, Direction.NORTH));
		return controller;
	}

	private ReactorControllerBlockEntity reactor(ServerLevel level, BlockPos pos) {
		return (ReactorControllerBlockEntity) level.getBlockEntity(pos);
	}

	private void onReactor(TestSingleplayerContext sp, BlockPos pos, Consumer<ReactorControllerBlockEntity> action) {
		sp.getServer().runOnServer(server -> action.accept(this.reactor(server.overworld(), pos)));
	}

	private void report(TestSingleplayerContext sp, BlockPos pos, String when) {
		String s = sp.getServer().computeOnServer(server -> {
			ReactorControllerBlockEntity r = this.reactor(server.overworld(), pos);
			if (r == null) return "no reactor";
			return String.format(Locale.ROOT, "%s: P=%.3f MW (fission %.3f) of %.0f MW, k=%.4f rho=%+.0f pcm period=%.0f s rods=%.1f%% scram=%s(%s) "
							+ "Tfuel=%.0f hot=%.0f Twater=%.0f level=%.0f%% p=%.1f bar steam=%.2f feed=%.2f out=%.2f vent=%.2f Xe=%.0f pcm decay=%.3f MW alarms=%s",
					when, r.thermalPower() / 1e6, r.power / 1e6, r.nominal() / 1e6, r.k(), r.rho * 1e5, r.period, r.rods * 100, r.scram, r.trip,
					r.fuelTemp, r.hotTemp, r.waterTemp, r.water / r.capacity() * 100, r.pressure, r.steamRate, r.feedRate, r.steamOutRate, r.ventRate,
					r.xenonWorth * 1e5, r.decayHeat() / 1e6, Integer.toBinaryString(r.alarms));
		});
		System.out.println("[reactor-tour] " + s);
	}

	private void shot(ClientGameTestContext context, String name) {
		context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		context.takeScreenshot(name);
	}

	private void look(ClientGameTestContext context, TestSingleplayerContext sp, double x, double y, double z, Vec3 target) {
		double dx = target.x - x, dy = target.y - (y + 1.62), dz = target.z - z;
		float yaw = (float) (-Math.toDegrees(Math.atan2(dx, dz)));
		float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
		sp.getServer().runOnServer(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				p.getAbilities().flying = true;
				p.onUpdateAbilities();
				p.setDeltaMovement(Vec3.ZERO);
			}
		});
		context.waitTicks(5);
		try {
			sp.getConnection().waitForChunksRender(false, 600);
		} catch (AssertionError e) {
			System.out.println("[reactor-tour] chunks still rendering");
		}
	}

	private float exposure(TestSingleplayerContext sp, double x, double y, double z) {
		return sp.getServer().computeOnServer(server -> RadiationApi.exposureAt(server.overworld(), new Vec3(x, y, z)));
	}

	// ------------------------------------------------------------------ 1: a power plant

	private void plant(ClientGameTestContext context, TestSingleplayerContext sp) {
		int y = this.g;
		BlockPos controller = sp.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			BlockPos c = core(level, 0, y, 0, true);
			// feedwater: a pump in a pond, powered at 10 kV by a diesel set and a step-up transformer
			for (BlockPos p : BlockPos.betweenClosed(-7, y - 1, 0, -5, y - 1, 4)) set(level, p.immutable(), Blocks.STONE.defaultBlockState());
			for (BlockPos p : BlockPos.betweenClosed(-7, y, 0, -5, y, 4)) set(level, p.immutable(), Blocks.WATER.defaultBlockState());
			for (int dz = -1; dz <= 5; dz++) {
				set(level, new BlockPos(-8, y, dz), Blocks.STONE.defaultBlockState());
				set(level, new BlockPos(-4, y, dz == 2 ? -1 : dz), Blocks.STONE.defaultBlockState());
			}
			for (int dx = -8; dx <= -4; dx++) {
				set(level, new BlockPos(dx, y, -1), Blocks.STONE.defaultBlockState());
				set(level, new BlockPos(dx, y, 5), Blocks.STONE.defaultBlockState());
			}
			set(level, new BlockPos(-4, y, 2), ModBlocks.FEEDWATER_PUMP.defaultBlockState());
			pipe(level, new BlockPos(-3, y, 2), ModBlocks.WATER_PIPE);
			pipe(level, new BlockPos(-2, y, 2), ModBlocks.WATER_PIPE);
			set(level, new BlockPos(-4, y + 1, 2), Blocks.AIR.defaultBlockState());
			// 10 kV for the pump: diesel generator -> step-up transformer -> MV cable onto the pump
			set(level, new BlockPos(-4, y + 1, 6), Blocks.AIR.defaultBlockState());
			set(level, new BlockPos(-3, y, 8), net.antwire.gridworks.registry.ModBlocks.GENERATOR.defaultBlockState().setValue(GeneratorBlock.FACING, Direction.SOUTH));
			set(level, new BlockPos(-2, y, 8), net.antwire.gridworks.registry.ModBlocks.GENERATOR.defaultBlockState().setValue(GeneratorBlock.FACING, Direction.SOUTH));
			for (BlockPos gp : new BlockPos[]{new BlockPos(-3, y, 8), new BlockPos(-2, y, 8)}) {
				if (level.getBlockEntity(gp) instanceof GeneratorBlockEntity gen) gen.setItem(0, new ItemStack(Items.COAL_BLOCK, 64));
			}
			cable(level, new BlockPos(-3, y, 7), ConductorType.LV_UNDERGROUND_CABLE);
			cable(level, new BlockPos(-2, y, 7), ConductorType.LV_UNDERGROUND_CABLE);
			set(level, new BlockPos(-3, y, 6), net.antwire.gridworks.registry.ModBlocks.DISTRIBUTION_TRANSFORMER.defaultBlockState()
					.setValue(ConverterBlock.FACING, Direction.SOUTH).setValue(ConverterBlock.STEP_UP, true));
			cable(level, new BlockPos(-3, y, 5), ConductorType.MV_CABLE);
			cable(level, new BlockPos(-3, y, 4), ConductorType.MV_CABLE);
			cable(level, new BlockPos(-3, y, 3), ConductorType.MV_CABLE);
			cable(level, new BlockPos(-4, y, 3), ConductorType.MV_CABLE);
			// main steam: outlet -> steam pipe -> turbine -> generator -> step-down -> heaters and lamps
			for (int dx = 6; dx <= 8; dx++) pipe(level, new BlockPos(dx, y + 2, 2), ModBlocks.STEAM_PIPE);
			set(level, new BlockPos(7, y + 2, 3), ModBlocks.CONDENSER.defaultBlockState());
			pipe(level, new BlockPos(7, y + 2, 2), ModBlocks.STEAM_PIPE);
			set(level, new BlockPos(9, y + 2, 2), ModBlocks.STEAM_TURBINE.defaultBlockState().setValue(SteamTurbineBlock.FACING, Direction.EAST));
			set(level, new BlockPos(10, y + 2, 2), ModBlocks.TURBINE_GENERATOR.defaultBlockState().setValue(TurbineGeneratorBlock.FACING, Direction.WEST));
			cable(level, new BlockPos(11, y + 2, 2), ConductorType.MV_CABLE);
			set(level, new BlockPos(12, y + 2, 2), net.antwire.gridworks.registry.ModBlocks.DISTRIBUTION_TRANSFORMER.defaultBlockState()
					.setValue(ConverterBlock.FACING, Direction.EAST));
			for (int dx = 13; dx <= 15; dx++) cable(level, new BlockPos(dx, y + 2, 2), ConductorType.LV_UNDERGROUND_CABLE);
			for (int dx = 13; dx <= 15; dx++) {
				set(level, new BlockPos(dx, y + 3, 2), net.antwire.gridworks.registry.ModBlocks.ELECTRIC_HEATER.defaultBlockState().setValue(LampBlock.FACING, Direction.UP));
			}
			// control room north of the reactor
			BlockPos console = new BlockPos(2, y, -10);
			set(level, console, ModBlocks.REACTOR_CONSOLE.defaultBlockState().setValue(ControlRoomBlock.FACING, Direction.NORTH));
			set(level, new BlockPos(4, y, -10), ModBlocks.SCRAM_BUTTON.defaultBlockState().setValue(ControlRoomBlock.FACING, Direction.NORTH));
			for (int dx = -1; dx <= 5; dx++) {
				for (int dy = 0; dy <= 3; dy++) set(level, new BlockPos(dx, y + dy, -8), Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.LIGHT_GRAY).defaultBlockState());
			}
			set(level, new BlockPos(1, y + 2, -9), ModBlocks.ANNUNCIATOR_PANEL.defaultBlockState().setValue(ControlRoomBlock.FACING, Direction.NORTH));
			set(level, new BlockPos(2, y + 2, -9), ModBlocks.CORE_MAP.defaultBlockState().setValue(ControlRoomBlock.FACING, Direction.NORTH));
			set(level, new BlockPos(3, y + 2, -9), ModBlocks.RADIATION_MONITOR.defaultBlockState().setValue(net.antwire.fission.block.RadiationMonitorBlock.FACING, Direction.NORTH));
			for (BlockPos p : new BlockPos[]{console, new BlockPos(4, y, -10), new BlockPos(1, y + 2, -9), new BlockPos(2, y + 2, -9)}) {
				if (level.getBlockEntity(p) instanceof LinkedBlockEntity l) l.link(c);
			}
			return c;
		});
		context.waitTicks(60);
		this.report(sp, controller, "plant built, rods in");
		// the core is flooded; for the test the start-up from the source range is skipped: 300 kW, then AUTO to 3 MW
		this.onReactor(sp, controller, r -> {
			r.water = r.capacity();
			r.rods = 0.5;
			r.setRodTarget(0.5);
		});
		context.waitTicks(40);
		this.onReactor(sp, controller, r -> System.out.println(String.format(Locale.ROOT, "[reactor-tour] k with rods at %.2f: %.4f", r.rods, r.k())));
		this.onReactor(sp, controller, r -> {
			r.primePower(3e5);
			r.setAuto(true);
			r.setpoint = 8e6;
		});
		for (int i = 0; i < 17; i++) {
			context.waitTicks(400);
			this.report(sp, controller, "auto " + (i + 1) * 20 + " s");
		}
		String plant = sp.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			SteamTurbineBlockEntity t = (SteamTurbineBlockEntity) level.getBlockEntity(new BlockPos(9, this.g + 2, 2));
			var pump = (net.antwire.fission.block.entity.FeedwaterPumpBlockEntity) level.getBlockEntity(new BlockPos(-4, this.g, 2));
			boolean heater = level.getBlockState(new BlockPos(13, this.g + 3, 2)).getValue(LampBlock.LIT);
			return String.format(Locale.ROOT, "turbine %.0f rpm steam %.2f kg/s electric %.3f MW | pump powered=%s flow %.2f | heater lit=%s",
					t.rpm, t.steamIn, t.electric / 1e6, pump.powered(), pump.flow(), heater);
		});
		System.out.println("[reactor-tour] " + plant);
		System.out.println(String.format(Locale.ROOT, "[reactor-tour] radiation: 3 blocks east of core %.2f rad/s, control room %.3f rad/s",
				this.exposure(sp, 10.5, this.g + 1, -2), this.exposure(sp, 2.5, this.g + 1, -11)));
		this.look(context, sp, -9.5, this.g + 9, -9.5, new Vec3(4, this.g + 2, 3));
		this.shot(context, "plant_overview");
		this.look(context, sp, 11.5, this.g + 4, -3.5, new Vec3(10, this.g + 2, 2));
		this.shot(context, "turbine_hall");
		this.look(context, sp, 2.5, this.g, -13, new Vec3(2.5, this.g + 1.5, -9));
		this.shot(context, "control_room");
		context.runOnClient(mc -> mc.gui.setScreen(new ReactorScreen(controller)));
		context.waitTicks(30);
		this.shot(context, "console_running");
		context.runOnClient(mc -> {
			if (mc.gui.screen() instanceof ReactorScreen s) s.openCover();
		});
		// SCRAM from the pedestal
		sp.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			BlockPos b = new BlockPos(4, this.g, -10);
			BlockState st = level.getBlockState(b);
			level.setBlock(b, st.setValue(ControlRoomBlock.OPEN, true), Block.UPDATE_ALL);
			((LinkedBlockEntity) level.getBlockEntity(b)).controller().scram("Manual SCRAM (test)");
		});
		context.waitTicks(60);
		this.shot(context, "console_scram");
		this.report(sp, controller, "3 s after SCRAM");
		context.runOnClient(mc -> mc.gui.setScreen(null));
		this.look(context, sp, 2.5, this.g, -13, new Vec3(2.5, this.g + 1.5, -9));
		this.shot(context, "control_room_scram");
		context.waitTicks(400);
		this.report(sp, controller, "23 s after SCRAM");
	}

	// ------------------------------------------------------------------ 2: fission products

	private void fuelCycle(ClientGameTestContext context, TestSingleplayerContext sp) {
		int y = this.g, x = 40;
		sp.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			long now = level.getGameTime();
			// a spent LEU assembly that has run 15 MWd and cooled for a day and a bit
			FuelData spent = FuelData.fresh(FuelType.LEU).irradiate(1.0e6, 15 * 24000, now - 30000);
			ItemStack rod = new ItemStack(ModItems.LEU_FUEL_ROD);
			rod.set(ModComponents.FUEL, spent);
			set(level, new BlockPos(x, y, 0), ModBlocks.REPROCESSING_PLANT.defaultBlockState());
			((MachineBlockEntity) level.getBlockEntity(new BlockPos(x, y, 0))).setItem(0, rod);
			set(level, new BlockPos(x, y, 2), net.antwire.gridworks.registry.ModBlocks.GENERATOR.defaultBlockState().setValue(GeneratorBlock.FACING, Direction.SOUTH));
			((GeneratorBlockEntity) level.getBlockEntity(new BlockPos(x, y, 2))).setItem(0, new ItemStack(Items.COAL_BLOCK, 16));
			cable(level, new BlockPos(x, y, 1), ConductorType.POWER_CABLE);
			for (int dx = 1; dx <= 5; dx++) pipe(level, new BlockPos(x + dx, y, 0), ModBlocks.ISOTOPE_PIPE);
			set(level, new BlockPos(x + 3, y, -1), ModBlocks.HOLDING_BASIN.defaultBlockState());
			set(level, new BlockPos(x + 6, y, 0), ModBlocks.STORAGE_DRUM.defaultBlockState());
			set(level, new BlockPos(x + 5, y, 1), ModBlocks.STORAGE_DRUM.defaultBlockState());
			// for comparison: the same kind of rod alone in a fuel channel, unshielded
			set(level, new BlockPos(x, y, 10), ModBlocks.FUEL_CHANNEL.defaultBlockState());
			((FuelChannelBlockEntity) level.getBlockEntity(new BlockPos(x, y, 10))).setRod(rod.copy());
		});
		context.waitTicks(500);
		String s = sp.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			StringBuilder b = new StringBuilder("fuel cycle:");
			for (BlockPos p : new BlockPos[]{new BlockPos(x, y, 0), new BlockPos(x + 3, y, -1), new BlockPos(x + 6, y, 0), new BlockPos(x + 5, y, 1)}) {
				var c = (net.minecraft.world.Container) level.getBlockEntity(p);
				b.append(" [").append(level.getBlockState(p).getBlock().getName().getString()).append(":");
				for (int i = 0; i < c.getContainerSize(); i++) {
					ItemStack st = c.getItem(i);
					if (!st.isEmpty()) b.append(' ').append(st.getHoverName().getString()).append(st.getCount() > 1 ? " x" + st.getCount() : "");
				}
				if (c instanceof StorageBlockEntity sb) b.append(String.format(Locale.ROOT, " | contents %.1f rad/s at 1 m", sb.contentsRads));
				b.append(']');
			}
			return b.toString();
		});
		System.out.println("[reactor-tour] " + s);
		System.out.println(String.format(Locale.ROOT, "[reactor-tour] radiation 1 m from: basin %.3f, drum %.3f, bare spent rod %.1f rad/s",
				this.exposure(sp, x + 3.5, y + 0.5, -2.5), this.exposure(sp, x + 7.5, y + 0.5, 0.5), this.exposure(sp, x + 1.5, y + 0.5, 10.5)));
		this.look(context, sp, x + 2.5, y + 3, -6, new Vec3(x + 3, y, 0));
		this.shot(context, "fuel_cycle");
		for (BlockPos p : new BlockPos[]{new BlockPos(x, y, 0), new BlockPos(x + 3, y, -1), new BlockPos(x + 6, y, 0)}) {
			sp.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
					.openMenu((net.minecraft.world.MenuProvider) server.overworld().getBlockEntity(p)));
			context.waitTicks(20);
			this.shot(context, "gui_" + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
					sp.getServer().computeOnServer(server -> server.overworld().getBlockState(p).getBlock())).getPath());
			context.runOnClient(mc -> mc.gui.setScreen(null));
		}
	}

	// ------------------------------------------------------------------ 3: meltdown

	private void meltdown(ClientGameTestContext context, TestSingleplayerContext sp) {
		int y = this.g + 3, x = 70;
		BlockPos controller = sp.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			// a raised core next to a pool, on a floor of dirt on one side and reinforced concrete on the other
			for (BlockPos p : BlockPos.betweenClosed(x - 3, this.g, -3, x + 7, this.g + 2, 10)) set(level, p.immutable(), Blocks.DIRT.defaultBlockState());
			for (BlockPos p : BlockPos.betweenClosed(x + 2, this.g, -3, x + 7, this.g + 2, 10)) {
				set(level, p.immutable(), net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
						net.minecraft.resources.Identifier.parse("radiation:reinforced_concrete")).defaultBlockState());
			}
			for (BlockPos p : BlockPos.betweenClosed(x - 2, this.g + 2, 6, x + 6, this.g + 2, 9)) set(level, p.immutable(), Blocks.WATER.defaultBlockState());
			return core(level, x, y, 0, true);
		});
		context.waitTicks(60);
		// running at full power when the feedwater fails, with the protection system bypassed
		this.onReactor(sp, controller, r -> {
			r.rps = false;
			r.water = 0;
			r.fuelTemp = 800;
			r.rods = 0.3;
			r.setRodTarget(0.3);
			r.primePower(24e6);
		});
		for (int i = 0; i < 9; i++) {
			context.waitTicks(200);
			this.report(sp, controller, "meltdown " + (i + 1) * 10 + " s");
		}
		context.waitTicks(400);
		String c = sp.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			int molten = 0, minY = Integer.MAX_VALUE, water = 0;
			for (BlockPos p : BlockPos.betweenClosed(x - 4, this.g - 8, -4, x + 8, y + 5, 11)) {
				if (level.getBlockState(p).is(ModBlocks.CORIUM)) {
					molten++;
					minY = Math.min(minY, p.getY());
				}
				if (level.getFluidState(p).isSource() && level.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER)) water++;
			}
			return String.format(Locale.ROOT, "corium blocks %d, deepest at y %d (ground %d), water sources left in the pool %d of 36",
					molten, minY, this.g, water);
		});
		System.out.println("[reactor-tour] " + c);
		System.out.println(String.format(Locale.ROOT, "[reactor-tour] radiation 5 m from the melted core: %.0f rad/s", this.exposure(sp, x + 2.5, y + 2, -6)));
		this.look(context, sp, x - 6.5, y + 4, -6.5, new Vec3(x + 2, this.g + 1, 2));
		this.shot(context, "meltdown_core");
		// corium in the open: a pile of it on a reinforced concrete pad next to a pond
		int cx = x + 20;
		sp.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			Block rc = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("radiation:reinforced_concrete"));
			for (BlockPos p : BlockPos.betweenClosed(cx - 6, this.g - 2, -6, cx + 9, this.g - 1, 6)) set(level, p.immutable(), rc.defaultBlockState());
			for (BlockPos p : BlockPos.betweenClosed(cx + 3, this.g - 1, -3, cx + 8, this.g - 1, 3)) set(level, p.immutable(), Blocks.WATER.defaultBlockState());
			for (BlockPos p : BlockPos.betweenClosed(cx - 1, this.g, -1, cx + 1, this.g + 1, 1)) {
				net.antwire.fission.world.Corium.spawn(level, p.immutable(), 0);
			}
		});
		context.waitTicks(400);
		String demo = sp.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			int molten = 0, minY = Integer.MAX_VALUE, water = 0;
			for (BlockPos p : BlockPos.betweenClosed(cx - 6, this.g - 10, -6, cx + 10, this.g + 6, 6)) {
				if (level.getBlockState(p).is(ModBlocks.CORIUM)) {
					molten++;
					minY = Math.min(minY, p.getY());
				}
				if (level.getFluidState(p).isSource() && level.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER)) water++;
			}
			return String.format(Locale.ROOT, "corium demo after 20 s: %d molten blocks, deepest y %d (ground %d), %d of 42 water sources left", molten, minY, this.g, water);
		});
		System.out.println("[reactor-tour] " + demo);
		this.look(context, sp, cx - 6.5, this.g + 4, -9.5, new Vec3(cx + 2, this.g, 0));
		this.shot(context, "corium");
	}

	// ------------------------------------------------------------------ 4: explosions

	private void explosions(ClientGameTestContext context, TestSingleplayerContext sp) {
		int y = this.g;
		Block concrete = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("radiation:reinforced_concrete"));
		BlockPos[] cores = sp.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			BlockPos weak = core(level, 100, y, 0, true);
			BlockPos strong = core(level, 130, y, 0, true);
			// a roof of three layers of dirt and stone over the first, four layers of reinforced concrete over the second
			for (int dx = -2; dx <= 6; dx++) {
				for (int dz = -2; dz <= 6; dz++) {
					set(level, new BlockPos(100 + dx, y + 5, dz), Blocks.STONE.defaultBlockState());
					set(level, new BlockPos(100 + dx, y + 6, dz), Blocks.DIRT.defaultBlockState());
					set(level, new BlockPos(100 + dx, y + 7, dz), Blocks.GRASS_BLOCK.defaultBlockState());
					for (int l = 0; l < 4; l++) set(level, new BlockPos(130 + dx, y + 5 + l, dz), concrete.defaultBlockState());
					set(level, new BlockPos(130 + dx, y + 9, dz), Blocks.GRASS_BLOCK.defaultBlockState());
				}
			}
			return new BlockPos[]{weak, strong};
		});
		context.waitTicks(60);
		this.look(context, sp, 92.5, y + 6, -14.5, new Vec3(102, y + 4, 2));
		this.shot(context, "explosion_before");
		// an operator yanks all rods out with the protection system bypassed: prompt critical
		for (BlockPos c : cores) {
			this.onReactor(sp, c, r -> {
				r.water = r.capacity();
				r.rps = false;
				r.rods = 0;
				r.setRodTarget(0);
			});
		}
		context.waitTicks(12);
		this.shot(context, "explosion_flying");
		int flying = sp.getServer().computeOnServer(server -> server.overworld().getEntitiesOfClass(FallingBlockEntity.class,
				new AABB(80, y - 10, -30, 160, y + 120, 30)).size());
		System.out.println("[reactor-tour] blocks in the air right after the explosions: " + flying);
		context.waitTicks(120);
		this.shot(context, "explosion_after_weak");
		this.look(context, sp, 122.5, y + 9, -14.5, new Vec3(132, y + 6, 2));
		this.shot(context, "explosion_after_concrete");
		String s = sp.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			int weakRoof = 0, strongRoof = 0;
			for (int dx = 0; dx <= 4; dx++) {
				for (int dz = 0; dz <= 4; dz++) {
					if (!level.getBlockState(new BlockPos(100 + dx, y + 5, dz)).isAir()) weakRoof++;
					if (level.getBlockState(new BlockPos(130 + dx, y + 5, dz)).is(concrete)) strongRoof++;
				}
			}
			return String.format(Locale.ROOT, "roof blocks left over the core: earth roof %d of 25, concrete roof %d of 25", weakRoof, strongRoof);
		});
		System.out.println("[reactor-tour] " + s);
		System.out.println(String.format(Locale.ROOT, "[reactor-tour] radiation 20 m from the open crater %.1f rad/s, 20 m from the contained one %.2f rad/s",
				this.exposure(sp, 102, y + 2, -18), this.exposure(sp, 132, y + 2, -18)));
	}
}
