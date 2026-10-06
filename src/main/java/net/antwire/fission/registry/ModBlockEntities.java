package net.antwire.fission.registry;

import net.antwire.fission.Fission;
import net.antwire.fission.block.entity.FeedwaterPumpBlockEntity;
import net.antwire.fission.block.entity.FuelChannelBlockEntity;
import net.antwire.fission.block.entity.LinkedBlockEntity;
import net.antwire.fission.block.entity.Machines;
import net.antwire.fission.block.entity.RadiationMonitorBlockEntity;
import net.antwire.fission.block.entity.ReactorControllerBlockEntity;
import net.antwire.fission.block.entity.SteamTurbineBlockEntity;
import net.antwire.fission.block.entity.StorageBlockEntities;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class ModBlockEntities {
	public static final BlockEntityType<ReactorControllerBlockEntity> REACTOR_CONTROLLER = reg("reactor_controller", ReactorControllerBlockEntity::new,
			ModBlocks.REACTOR_CONTROLLER);
	public static final BlockEntityType<FuelChannelBlockEntity> FUEL_CHANNEL = reg("fuel_channel", FuelChannelBlockEntity::new, ModBlocks.FUEL_CHANNEL);
	public static final BlockEntityType<FeedwaterPumpBlockEntity> FEEDWATER_PUMP = reg("feedwater_pump", FeedwaterPumpBlockEntity::new, ModBlocks.FEEDWATER_PUMP);
	public static final BlockEntityType<SteamTurbineBlockEntity> STEAM_TURBINE = reg("steam_turbine", SteamTurbineBlockEntity::new, ModBlocks.STEAM_TURBINE);
	public static final BlockEntityType<StorageBlockEntities.FuelRack> FUEL_RACK = reg("fuel_rack", StorageBlockEntities.FuelRack::new, ModBlocks.FUEL_RACK);
	public static final BlockEntityType<StorageBlockEntities.HoldingBasin> HOLDING_BASIN = reg("holding_basin", StorageBlockEntities.HoldingBasin::new,
			ModBlocks.HOLDING_BASIN);
	public static final BlockEntityType<StorageBlockEntities.StorageDrum> STORAGE_DRUM = reg("storage_drum", StorageBlockEntities.StorageDrum::new,
			ModBlocks.STORAGE_DRUM);
	public static final BlockEntityType<Machines.Reprocessing> REPROCESSING = reg("reprocessing_plant", Machines.Reprocessing::new, ModBlocks.REPROCESSING_PLANT);
	public static final BlockEntityType<Machines.Centrifuge> CENTRIFUGE = reg("gas_centrifuge", Machines.Centrifuge::new, ModBlocks.GAS_CENTRIFUGE);
	public static final BlockEntityType<RadiationMonitorBlockEntity> RADIATION_MONITOR = reg("radiation_monitor", RadiationMonitorBlockEntity::new,
			ModBlocks.RADIATION_MONITOR);
	public static final BlockEntityType<LinkedBlockEntity> CONSOLE = linked("reactor_console", ModBlocks.REACTOR_CONSOLE);
	public static final BlockEntityType<LinkedBlockEntity> SCRAM_BUTTON = linked("scram_button", ModBlocks.SCRAM_BUTTON);
	public static final BlockEntityType<LinkedBlockEntity> ANNUNCIATOR = linked("annunciator_panel", ModBlocks.ANNUNCIATOR_PANEL);
	public static final BlockEntityType<LinkedBlockEntity> CORE_MAP = linked("core_map", ModBlocks.CORE_MAP);

	private ModBlockEntities() {
	}

	public static void init() {
	}

	private static <T extends BlockEntity> BlockEntityType<T> reg(String name, FabricBlockEntityTypeBuilder.Factory<T> factory, Block block) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Fission.id(name), FabricBlockEntityTypeBuilder.create(factory, block).build());
	}

	private static BlockEntityType<LinkedBlockEntity> linked(String name, Block block) {
		BlockEntityType<LinkedBlockEntity>[] holder = new BlockEntityType[1];
		holder[0] = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Fission.id(name),
				FabricBlockEntityTypeBuilder.create((BlockPos pos, BlockState state) -> new LinkedBlockEntity(holder[0], pos, state), block).build());
		return holder[0];
	}
}
