package net.antwire.fission.gametest;

import dev.radiation.api.RadiationApi;
import net.antwire.fission.nuclear.Canister;
import net.antwire.fission.nuclear.FuelData;
import net.antwire.fission.nuclear.FuelType;
import net.antwire.fission.nuclear.Isotope;
import net.antwire.fission.registry.ModComponents;
import net.antwire.fission.registry.ModItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Fission products where they do not belong (Fission 1.5): a spent assembly thrown into lava, one into a fire, plutonium
 * onto a cactus, a canister of caesium-137 dropped on the grass and another one kept in an ordinary chest. They all
 * survive, radiate freely and contaminate the ground under them. Run with {@code ./gradlew runClientGameTest -Pscenes=loose}.
 */
public class LooseTour implements FabricClientGameTest {
	private int g;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!System.getProperty("fission.scenes", "").contains("loose") || !System.getProperty("fission.map", "").isEmpty()) {
			return;
		}
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
			sp.getServer().runOnServer(server -> this.build(server.overworld()));
			context.waitTicks(100);
			System.out.println("[loose] after 5 s: " + sp.getServer().computeOnServer(server -> this.report(server.overworld())));
			sp.getServer().runOnServer(server -> server.getPlayerList().getPlayers().get(0).teleportTo(4.5, this.g + 2, -6.5));
			context.waitTicks(10);
			context.getInput().lookAt(new BlockPos(4, this.g, 2));
			context.waitTicks(20);
			context.takeScreenshot("loose_lava_fire_cactus");
			context.waitTicks(1100);
			System.out.println("[loose] after 1 minute: " + sp.getServer().computeOnServer(server -> this.report(server.overworld())));
		}
	}

	private static ItemStack spentRod(long now) {
		ItemStack rod = new ItemStack(ModItems.LEU_FUEL_ROD);
		rod.set(ModComponents.FUEL, FuelData.fresh(FuelType.LEU).irradiate(1.0e6, 15 * 24000, now - 30000));
		return rod;
	}

	private static ItemStack caesium(long now) {
		ItemStack can = new ItemStack(ModItems.ISOTOPE_CANISTER);
		// about 1 PBq of caesium-137
		can.set(ModComponents.CANISTER, new Canister(Isotope.CS137, 1.0e15 / Isotope.CS137.lambda, now));
		return can;
	}

	private void build(ServerLevel level) {
		long now = level.getGameTime();
		int y = this.g;
		// a pool of lava, a fire and a cactus in a row
		level.setBlock(new BlockPos(0, y - 1, 2), Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(new BlockPos(3, y - 1, 2), Blocks.NETHERRACK.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(new BlockPos(3, y, 2), Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(new BlockPos(6, y - 1, 2), Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(new BlockPos(6, y, 2), Blocks.CACTUS.defaultBlockState(), Block.UPDATE_ALL);
		drop(level, spentRod(now), 0.5, y + 1.5, 2.5);
		drop(level, spentRod(now), 3.5, y + 1.5, 2.5);
		drop(level, new ItemStack(ModItems.PLUTONIUM, 8), 6.5, y + 2.5, 2.5);
		// caesium on the grass, caesium in a chest
		drop(level, caesium(now), 20.5, y + 0.5, 2.5);
		level.setBlock(new BlockPos(30, y, 2), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		((ChestBlockEntity) level.getBlockEntity(new BlockPos(30, y, 2))).setItem(0, caesium(now));
	}

	private static void drop(ServerLevel level, ItemStack stack, double x, double y, double z) {
		ItemEntity e = new ItemEntity(level, x, y, z, stack);
		e.setDeltaMovement(0, 0, 0);
		level.addFreshEntity(e);
	}

	private String report(ServerLevel level) {
		StringBuilder b = new StringBuilder();
		for (int x : new int[]{0, 3, 6, 20}) {
			var items = level.getEntitiesOfClass(ItemEntity.class, new AABB(x - 1, this.g - 3, 0, x + 2, this.g + 4, 5));
			b.append(String.format(Locale.ROOT, " | x=%d: %s", x, items.isEmpty() ? "GONE" : items.get(0).getItem().getCount() + "x " + items.get(0).getItem().getItem()));
		}
		b.append(String.format(Locale.ROOT, " | dose 2 m from the caesium on the grass %.2f rad/s, ground under it %.3f rad/s",
				RadiationApi.exposureAt(level, new Vec3(22.5, this.g + 1, 2.5)), RadiationApi.contaminationAt(level, new BlockPos(20, this.g, 2))));
		b.append(String.format(Locale.ROOT, " | dose 2 m from the chest %.2f rad/s, floor under it %.3f rad/s",
				RadiationApi.exposureAt(level, new Vec3(32.5, this.g + 1, 2.5)), RadiationApi.contaminationAt(level, new BlockPos(30, this.g, 2))));
		return b.toString();
	}
}
