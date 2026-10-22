package net.antwire.fission.gametest;

import net.antwire.fission.block.entity.StorageBlockEntity;
import net.antwire.fission.castor.CastorWagon;
import net.antwire.fission.castor.GantryCraneBlock;
import net.antwire.fission.castor.GantryCraneBlockEntity;
import net.antwire.fission.castor.ShuntingLocomotive;
import net.antwire.fission.nuclear.Canister;
import net.antwire.fission.nuclear.FuelData;
import net.antwire.fission.nuclear.FuelType;
import net.antwire.fission.nuclear.Isotope;
import net.antwire.fission.registry.ModBlocks;
import net.antwire.fission.registry.ModCastor;
import net.antwire.fission.registry.ModComponents;
import net.antwire.fission.registry.ModItems;
import net.antwire.gridworks.block.GeneratorBlock;
import net.antwire.gridworks.block.entity.GeneratorBlockEntity;
import net.antwire.gridworks.grid.ConductorType;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * The CASTOR chain on a straight siding: at the "plant" a gantry crane (LOAD) fills a CASTOR from a holding basin and a
 * storage drum; the shunter waits at the station rail until the crane is done, pulls the wagon on to the "repository",
 * where a second crane (UNLOAD) puts everything into final repository cells. Run with -Pscenes=castor.
 */
public class CastorTour {
	private static final int Z = 120;
	private int g;

	public void run(ClientGameTestContext context, TestSingleplayerContext sp, int ground) {
		this.g = ground;
		int y = this.g, x0 = 0;
		// plant crane: cab at (x0, y, Z), facing east: span x0..x0+8, Z..Z+6; the track runs along Z+3
		int craneA = x0, craneB = x0 + 30;
		int track = Z + 3;
		sp.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			long now = level.getGameTime();
			for (int x = x0 - 12; x <= craneB + 12; x++) {
				set(level, new BlockPos(x, y - 1, track), Blocks.GRAVEL.defaultBlockState());
				set(level, new BlockPos(x, y, track), (x == craneA + 5 || x == craneB + 5 ? ModCastor.STATION_RAIL : Blocks.RAIL).defaultBlockState());
			}
			for (int cab : new int[]{craneA, craneB}) {
				set(level, new BlockPos(cab, y, Z), ModCastor.GANTRY_CRANE.defaultBlockState().setValue(GantryCraneBlock.FACING, Direction.EAST));
				// a diesel generator behind the cab, coupled by a power cable
				cable(level, new BlockPos(cab - 1, y, Z));
				set(level, new BlockPos(cab - 2, y, Z), net.antwire.gridworks.registry.ModBlocks.GENERATOR.defaultBlockState()
						.setValue(GeneratorBlock.FACING, Direction.SOUTH));
				((GeneratorBlockEntity) level.getBlockEntity(new BlockPos(cab - 2, y, Z))).setItem(0, new ItemStack(Items.COAL_BLOCK, 32));
			}
			((GantryCraneBlockEntity) level.getBlockEntity(new BlockPos(craneA, y, Z))).setMode(GantryCraneBlockEntity.Mode.LOAD);
			((GantryCraneBlockEntity) level.getBlockEntity(new BlockPos(craneB, y, Z))).setMode(GantryCraneBlockEntity.Mode.UNLOAD);
			// at the plant: two spent assemblies cooled for more than a day in a basin, Cs-137 and plutonium in a drum
			set(level, new BlockPos(craneA + 3, y, Z + 1), ModBlocks.HOLDING_BASIN.defaultBlockState());
			set(level, new BlockPos(craneA + 6, y, Z + 5), ModBlocks.STORAGE_DRUM.defaultBlockState());
			FuelData spent = FuelData.fresh(FuelType.LEU).irradiate(1.0e6, 15 * 24000, now - 30000);
			StorageBlockEntity basin = (StorageBlockEntity) level.getBlockEntity(new BlockPos(craneA + 3, y, Z + 1));
			for (int i = 0; i < 2; i++) {
				ItemStack rod = new ItemStack(ModItems.LEU_FUEL_ROD);
				rod.set(ModComponents.FUEL, spent);
				basin.setItem(i, rod);
			}
			StorageBlockEntity drum = (StorageBlockEntity) level.getBlockEntity(new BlockPos(craneA + 6, y, Z + 5));
			ItemStack cs = new ItemStack(ModItems.ISOTOPE_CANISTER);
			cs.set(ModComponents.CANISTER, new Canister(Isotope.CS137, 2.0e22, now));
			drum.setItem(0, cs);
			drum.setItem(1, new ItemStack(ModItems.PLUTONIUM));
			// at the repository: three disposal cells beside the track
			for (int i = 0; i < 3; i++) {
				set(level, new BlockPos(craneB + 2 + 2 * i, y - 1, Z + 1), ModCastor.FINAL_REPOSITORY.defaultBlockState());
			}
			// the train west of the plant: the shunter in front, the CASTOR behind it
			spawn(level, ModCastor.SHUNTING_LOCOMOTIVE, x0 - 6.5, y, track + 0.5);
			spawn(level, ModCastor.CASTOR_WAGON, x0 - 7.8, y, track + 0.5);
		});
		context.waitTicks(40);
		sp.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			ShuntingLocomotive loco = level.getEntitiesOfClass(ShuntingLocomotive.class, box(x0 - 20, y, track, 60)).getFirst();
			loco.start(level, new Vec3(1, 0, 0));
			System.out.println("[castor-tour] shunter started with " + loco.wagons() + " wagon(s) coupled");
		});
		this.look(context, sp, x0 - 3, y + 6, Z - 7, new Vec3(x0 + 4, y + 2, track));
		boolean shotLoading = false;
		for (int s = 0; s < 120; s++) {
			context.waitTicks(20);
			String st = this.state(sp, x0, y, track, craneA, craneB);
			if (s % 5 == 0) System.out.println("[castor-tour] t=" + s + " s " + st);
			if (!shotLoading && st.contains("craneA=busy") && st.contains("hook=")) {
				context.waitTicks(25);
				this.shot(context, "castor_loading");
				shotLoading = true;
			}
			if (st.contains("atB")) {
				break;
			}
		}
		this.look(context, sp, craneB - 2, y + 6, Z - 7, new Vec3(craneB + 4, y + 2, track));
		boolean shotUnloading = false;
		for (int s = 0; s < 120; s++) {
			context.waitTicks(20);
			String st = this.state(sp, x0, y, track, craneA, craneB);
			if (s % 5 == 0) System.out.println("[castor-tour] t=" + (120 + s) + " s " + st);
			if (!shotUnloading && st.contains("craneB=busy") && st.contains("hook=")) {
				context.waitTicks(25);
				this.shot(context, "castor_unloading");
				shotUnloading = true;
			}
			if (st.contains("castor=0") && st.contains("craneB=idle")) {
				break;
			}
		}
		String result = sp.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			int stored = 0;
			for (int i = 0; i < 3; i++) {
				var c = (StorageBlockEntity) level.getBlockEntity(new BlockPos(craneB + 2 + 2 * i, y - 1, Z + 1));
				for (int k = 0; k < c.getContainerSize(); k++) if (!c.getItem(k).isEmpty()) stored++;
			}
			int leftBasin = count((StorageBlockEntity) level.getBlockEntity(new BlockPos(craneA + 3, y, Z + 1)));
			int leftDrum = count((StorageBlockEntity) level.getBlockEntity(new BlockPos(craneA + 6, y, Z + 5)));
			return String.format(Locale.ROOT, "repository holds %d of 4, left at the plant: basin %d, drum %d", stored, leftBasin, leftDrum);
		});
		System.out.println("[castor-tour] RESULT " + result + (result.startsWith("repository holds 4 of 4, left at the plant: basin 0, drum 0") ? " OK" : " FAILED"));
		this.look(context, sp, craneB + 2, y + 5, track - 9, new Vec3(craneB + 4, y + 1, track));
		this.shot(context, "castor_repository");
		// the whole siding from above
		this.look(context, sp, x0 + 15, y + 22, Z - 14, new Vec3(x0 + 15, y, track));
		this.shot(context, "castor_siding");
	}

	private static int count(StorageBlockEntity s) {
		int n = 0;
		for (int k = 0; k < s.getContainerSize(); k++) if (!s.getItem(k).isEmpty()) n++;
		return n;
	}

	private String state(TestSingleplayerContext sp, int x0, int y, int track, int craneA, int craneB) {
		return sp.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			var box = box(x0 - 20, y, track, 80);
			StringBuilder b = new StringBuilder();
			for (ShuntingLocomotive l : level.getEntitiesOfClass(ShuntingLocomotive.class, box)) {
				b.append(String.format(Locale.ROOT, "loco x=%.2f v=%.3f hold=%s coupled=%d ", l.getX(), l.getDeltaMovement().horizontalDistance(), l.holding(), l.wagons()));
			}
			for (CastorWagon w : level.getEntitiesOfClass(CastorWagon.class, box)) {
				int n = 0;
				for (int k = 0; k < w.getContainerSize(); k++) if (!w.getItem(k).isEmpty()) n++;
				b.append(String.format(Locale.ROOT, "wagon x=%.2f castor=%d out=%.4f rad/s, 1 m beside it %.4f rad/s %s", w.getX(), n, w.outsideRads,
						dev.radiation.api.RadiationApi.exposureAt(level, w.position().add(0, 0.8, 1.0)), w.getX() > craneB + 1 ? "atB " : ""));
			}
			for (int[] c : new int[][]{{craneA, 'A'}, {craneB, 'B'}}) {
				var crane = (GantryCraneBlockEntity) level.getBlockEntity(new BlockPos(c[0], y, Z));
				b.append("crane").append((char) c[1]).append('=').append(crane.busy() ? "busy" : "idle")
						.append(crane.carrying().isEmpty() ? "" : " hook=" + crane.carrying().getHoverName().getString())
						.append(crane.powered() ? "" : " (no power)").append(' ');
			}
			return b.toString();
		});
	}

	private static net.minecraft.world.phys.AABB box(int x, int y, int z, int length) {
		return new net.minecraft.world.phys.AABB(x, y - 2, z - 2, x + length, y + 3, z + 3);
	}

	private static <T extends AbstractMinecart> void spawn(ServerLevel level, EntityType<T> type, double x, double y, double z) {
		T cart = type.create(level, EntitySpawnReason.COMMAND);
		cart.setInitialPos(x, y + 0.0625, z);
		level.addFreshEntity(cart);
	}

	private static void set(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_ALL);
	}

	private static void cable(ServerLevel level, BlockPos pos) {
		level.setBlock(pos, Block.updateFromNeighbourShapes(net.antwire.gridworks.registry.ModBlocks.CABLES.get(ConductorType.POWER_CABLE).defaultBlockState(),
				level, pos), Block.UPDATE_ALL);
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
			System.out.println("[castor-tour] chunks still rendering");
		}
	}
}
