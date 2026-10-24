package net.antwire.fission.gametest;

import net.antwire.fission.block.entity.RtgBlockEntity;
import net.antwire.fission.block.entity.StorageBlockEntity;
import net.antwire.fission.nuclear.HeatSource;
import net.antwire.fission.nuclear.Nuclear;
import net.antwire.fission.registry.ModBlocks;
import net.antwire.fission.registry.ModComponents;
import net.antwire.fission.registry.ModItems;
import net.antwire.gridworks.block.entity.BatteryBlockEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.util.Locale;

/**
 * Fission 1.7: a radioisotope generator with four Pu-238 heat sources (one 30 years old) charging a 12 V battery and
 * lighting an LED lamp, its screen, and the 54-place fuel rack. Run with {@code ./gradlew runClientGameTest -Pscenes=rtg}.
 */
public class RtgTour implements FabricClientGameTest {
	private int g;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!System.getProperty("fission.scenes", "").contains("rtg") || !System.getProperty("fission.map", "").isEmpty()) {
			return;
		}
		context.getInput().resizeWindow(1280, 720);
		try (TestSingleplayerContext sp = context.worldBuilder()
				.setUseConsistentSettings(true)
				.adjustSettings(s -> s.getNormalPresetList().stream().filter(e -> e.preset() != null && e.preset().is(WorldPresets.FLAT)).findFirst()
						.ifPresent(s::setWorldType))
				.create()) {
			sp.getConnection().waitForChunksRender();
			for (String c : new String[]{"time set 18000", "weather clear", "gamerule advance_time false", "gamerule advance_weather false",
					"gamemode creative @a", "gamerule spawn_mobs false"}) {
				sp.getServer().runCommand(c);
			}
			this.g = sp.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				int y = level.getMinY() + 4;
				while (!level.getBlockState(new BlockPos(0, y, 0)).isAir()) y++;
				return y;
			});
			sp.getServer().runOnServer(server -> this.build(server.overworld()));
			context.waitTicks(100);
			System.out.println("[rtg] after 5 s: " + sp.getServer().computeOnServer(server -> this.report(server.overworld())));
			context.waitTicks(400);
			System.out.println("[rtg] after 25 s: " + sp.getServer().computeOnServer(server -> this.report(server.overworld())));
			sp.getServer().runOnServer(server -> server.getPlayerList().getPlayers().get(0).teleportTo(0.5, this.g + 1, -3.5));
			context.waitTicks(10);
			context.getInput().lookAt(new BlockPos(0, this.g, 0));
			context.waitTicks(20);
			context.takeScreenshot("rtg_night");
			this.open(context, sp, new BlockPos(0, this.g, 0));
			context.takeScreenshot("rtg_screen");
			context.runOnClient(mc -> mc.gui.setScreen(null));
			this.open(context, sp, new BlockPos(4, this.g, 0));
			context.takeScreenshot("fuel_rack_54");
			context.runOnClient(mc -> mc.gui.setScreen(null));
		}
	}

	private void open(ClientGameTestContext context, TestSingleplayerContext sp, BlockPos pos) {
		sp.getServer().runOnServer(server -> {
			ServerPlayer p = server.getPlayerList().getPlayers().get(0);
			p.openMenu((MenuProvider) server.overworld().getBlockEntity(pos));
		});
		context.waitTicks(20);
	}

	private void build(ServerLevel level) {
		long now = level.getGameTime();
		int y = this.g;
		level.setBlock(new BlockPos(0, y, 0), ModBlocks.RTG.defaultBlockState(), Block.UPDATE_ALL);
		RtgBlockEntity rtg = (RtgBlockEntity) level.getBlockEntity(new BlockPos(0, y, 0));
		for (int i = 0; i < 3; i++) {
			rtg.setItem(i, new ItemStack(ModItems.PU238_HEAT_SOURCE));
		}
		ItemStack old = new ItemStack(ModItems.PU238_HEAT_SOURCE);
		old.set(ModComponents.HEAT_SOURCE, new HeatSource(now - (long) (30 * 365.25 * 86400 / Nuclear.GAME_SECONDS_PER_TICK)));
		rtg.setItem(3, old);
		level.setBlock(new BlockPos(1, y, 0), net.antwire.gridworks.registry.ModBlocks.BATTERY.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(new BlockPos(-1, y, 0), net.antwire.gridworks.registry.ModBlocks.LED_LAMP.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(new BlockPos(4, y, 0), ModBlocks.FUEL_RACK.defaultBlockState(), Block.UPDATE_ALL);
		StorageBlockEntity rack = (StorageBlockEntity) level.getBlockEntity(new BlockPos(4, y, 0));
		for (int i = 0; i < 40; i++) {
			rack.setItem(i, new ItemStack(ModItems.LEU_FUEL_ROD));
		}
	}

	private String report(ServerLevel level) {
		RtgBlockEntity rtg = (RtgBlockEntity) level.getBlockEntity(new BlockPos(0, this.g, 0));
		BatteryBlockEntity bat = (BatteryBlockEntity) level.getBlockEntity(new BlockPos(1, this.g, 0));
		StorageBlockEntity rack = (StorageBlockEntity) level.getBlockEntity(new BlockPos(4, this.g, 0));
		boolean stamped = rtg.getItem(0).has(ModComponents.HEAT_SOURCE);
		return String.format(Locale.ROOT, "RTG %.2f W (stamped %s, old one %.2f W), battery %.3f Wh, lamp %s, rack %d places, %.3f rad/s inside",
				rtg.watts(), stamped, net.antwire.fission.item.HeatSourceItem.watts(rtg.getItem(3), level.getGameTime()), bat.storedWh(),
				level.getBlockState(new BlockPos(-1, this.g, 0)), rack.getContainerSize(), rack.contentsRads);
	}
}
