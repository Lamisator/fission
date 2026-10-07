package net.antwire.fission.registry;

import net.antwire.fission.Fission;
import net.antwire.fission.block.CondenserBlock;
import net.antwire.fission.block.ContainerBlock;
import net.antwire.fission.block.ControlRodBlock;
import net.antwire.fission.block.ControlRoomBlock;
import net.antwire.fission.block.CorePartBlock;
import net.antwire.fission.block.CoriumBlock;
import net.antwire.fission.block.FeedwaterPumpBlock;
import net.antwire.fission.block.FuelChannelBlock;
import net.antwire.fission.block.PipeBlock;
import net.antwire.fission.block.EjectaBlock;
import net.antwire.fission.block.RadiatingBlock;
import net.antwire.fission.block.RadiationMonitorBlock;
import net.antwire.fission.block.ReactorControllerBlock;
import net.antwire.fission.block.SteamTurbineBlock;
import net.antwire.fission.block.TubeBlock;
import net.antwire.fission.block.TurbineGeneratorBlock;
import net.antwire.fission.block.entity.MachineBlockEntity;
import net.antwire.fission.block.entity.Machines;
import net.antwire.fission.block.entity.StorageBlockEntities;
import net.antwire.fission.block.entity.StorageBlockEntity;
import net.antwire.fission.reactor.CoreRole;
import net.antwire.fission.world.PipeMedium;
import net.antwire.fission.world.TubeKind;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class ModBlocks {
	/** Creative tab order (and which blocks get an item). */
	public static final List<Block> WITH_ITEMS = new ArrayList<>();

	public static final Block URANIUM_ORE = block("uranium_ore", p -> new DropExperienceBlock(UniformInt.of(1, 3), p),
			props(MapColor.STONE, 3.0F, SoundType.STONE).requiresCorrectToolForDrops(), true);
	public static final Block DEEPSLATE_URANIUM_ORE = block("deepslate_uranium_ore", p -> new DropExperienceBlock(UniformInt.of(1, 3), p),
			props(MapColor.DEEPSLATE, 4.5F, SoundType.DEEPSLATE).requiresCorrectToolForDrops(), true);

	// core
	public static final Block FUEL_CHANNEL = block("fuel_channel", FuelChannelBlock::new,
			props(MapColor.METAL, 5.0F, SoundType.METAL).requiresCorrectToolForDrops().lightLevel(s -> s.getValue(FuelChannelBlock.GLOW) ? 9 : 0)
					.pushReaction(PushReaction.IMMOVEABLE), true);
	public static final Block CONTROL_ROD = block("control_rod", ControlRodBlock::new,
			props(MapColor.METAL, 5.0F, SoundType.METAL).requiresCorrectToolForDrops().pushReaction(PushReaction.IMMOVEABLE), true);
	public static final Block GRAPHITE_MODERATOR = block("graphite_moderator", p -> new CorePartBlock(CoreRole.GRAPHITE, p),
			props(MapColor.COLOR_BLACK, 3.0F, SoundType.STONE).requiresCorrectToolForDrops(), true);
	public static final Block BERYLLIUM_REFLECTOR = block("beryllium_reflector", p -> new CorePartBlock(CoreRole.REFLECTOR, p),
			props(MapColor.COLOR_LIGHT_GRAY, 4.0F, SoundType.METAL).requiresCorrectToolForDrops(), true);
	public static final Block REACTOR_VESSEL = block("reactor_vessel", p -> new CorePartBlock(CoreRole.VESSEL, p),
			props(MapColor.METAL, 8.0F, SoundType.METAL).requiresCorrectToolForDrops().explosionResistance(30), true);
	public static final Block FEEDWATER_INLET = block("feedwater_inlet", p -> new CorePartBlock(CoreRole.INLET, p),
			props(MapColor.METAL, 8.0F, SoundType.METAL).requiresCorrectToolForDrops(), true);
	public static final Block STEAM_OUTLET = block("steam_outlet", p -> new CorePartBlock(CoreRole.OUTLET, p),
			props(MapColor.METAL, 8.0F, SoundType.METAL).requiresCorrectToolForDrops(), true);
	public static final Block RELIEF_VALVE = block("relief_valve", p -> new CorePartBlock(CoreRole.RELIEF, p),
			props(MapColor.METAL, 8.0F, SoundType.METAL).requiresCorrectToolForDrops(), true);
	public static final Block REACTOR_CONTROLLER = block("reactor_controller", ReactorControllerBlock::new,
			props(MapColor.COLOR_GRAY, 5.0F, SoundType.METAL).requiresCorrectToolForDrops(), true);

	// control room
	public static final Block REACTOR_CONSOLE = block("reactor_console", p -> new ControlRoomBlock(ControlRoomBlock.Kind.CONSOLE, p),
			props(MapColor.COLOR_GRAY, 3.0F, SoundType.METAL).noOcclusion(), true);
	public static final Block SCRAM_BUTTON = block("scram_button", p -> new ControlRoomBlock(ControlRoomBlock.Kind.SCRAM, p),
			props(MapColor.COLOR_RED, 2.0F, SoundType.METAL).noOcclusion(), true);
	public static final Block ANNUNCIATOR_PANEL = block("annunciator_panel", p -> new ControlRoomBlock(ControlRoomBlock.Kind.ANNUNCIATOR, p),
			props(MapColor.COLOR_GRAY, 2.0F, SoundType.METAL).lightLevel(s -> 4), true);
	public static final Block CORE_MAP = block("core_map", p -> new ControlRoomBlock(ControlRoomBlock.Kind.CORE_MAP, p),
			props(MapColor.COLOR_GRAY, 2.0F, SoundType.METAL).lightLevel(s -> 4), true);
	public static final Block RADIATION_MONITOR = block("radiation_monitor", RadiationMonitorBlock::new,
			props(MapColor.COLOR_YELLOW, 2.0F, SoundType.METAL).lightLevel(s -> s.getValue(RadiationMonitorBlock.ALARM) ? 10 : 3), true);

	// steam cycle
	public static final Block WATER_PIPE = block("water_pipe", p -> new PipeBlock(PipeMedium.WATER, p),
			props(MapColor.COLOR_BLUE, 2.0F, SoundType.METAL).noOcclusion(), true);
	public static final Block STEAM_PIPE = block("steam_pipe", p -> new PipeBlock(PipeMedium.STEAM, p),
			props(MapColor.METAL, 2.0F, SoundType.METAL).noOcclusion(), true);
	public static final Block FEEDWATER_PUMP = block("feedwater_pump", FeedwaterPumpBlock::new,
			props(MapColor.COLOR_BLUE, 4.0F, SoundType.METAL).requiresCorrectToolForDrops(), true);
	public static final Block STEAM_TURBINE = block("steam_turbine", SteamTurbineBlock::new,
			props(MapColor.COLOR_GREEN, 6.0F, SoundType.METAL).requiresCorrectToolForDrops(), true);
	public static final Block TURBINE_GENERATOR = block("turbine_generator", TurbineGeneratorBlock::new,
			props(MapColor.COLOR_YELLOW, 6.0F, SoundType.METAL).requiresCorrectToolForDrops(), true);
	public static final Block CONDENSER = block("condenser", CondenserBlock::new,
			props(MapColor.METAL, 4.0F, SoundType.METAL).requiresCorrectToolForDrops(), true);

	// fuel and waste
	public static final Block FUEL_TRANSFER_TUBE = block("fuel_transfer_tube", p -> new TubeBlock(TubeKind.FUEL, p),
			props(MapColor.COLOR_LIGHT_GRAY, 2.0F, SoundType.METAL).noOcclusion(), true);
	public static final Block ISOTOPE_PIPE = block("isotope_pipe", p -> new TubeBlock(TubeKind.ISOTOPE, p),
			props(MapColor.COLOR_YELLOW, 2.0F, SoundType.METAL).noOcclusion(), true);
	public static final Block FUEL_RACK = block("fuel_rack", p -> new ContainerBlock(StorageBlockEntities.FuelRack::new,
			() -> ModBlockEntities.FUEL_RACK, (net.minecraft.world.level.block.entity.BlockEntityTicker<StorageBlockEntity>) StorageBlockEntity::serverTick,
			Block.box(1, 0, 1, 15, 16, 15), p), props(MapColor.METAL, 3.0F, SoundType.METAL).noOcclusion(), true);
	public static final Block HOLDING_BASIN = block("holding_basin", p -> new ContainerBlock(StorageBlockEntities.HoldingBasin::new,
			() -> ModBlockEntities.HOLDING_BASIN, (net.minecraft.world.level.block.entity.BlockEntityTicker<StorageBlockEntity>) StorageBlockEntity::serverTick,
			null, p), props(MapColor.COLOR_LIGHT_BLUE, 4.0F, SoundType.STONE).lightLevel(s -> 6), true);
	public static final Block STORAGE_DRUM = block("storage_drum", p -> new ContainerBlock(StorageBlockEntities.StorageDrum::new,
			() -> ModBlockEntities.STORAGE_DRUM, (net.minecraft.world.level.block.entity.BlockEntityTicker<StorageBlockEntity>) StorageBlockEntity::serverTick,
			Block.box(2, 0, 2, 14, 15, 14), p), props(MapColor.COLOR_YELLOW, 4.0F, SoundType.METAL).noOcclusion(), true);
	public static final Block REPROCESSING_PLANT = block("reprocessing_plant", p -> new ContainerBlock(Machines.Reprocessing::new,
			() -> ModBlockEntities.REPROCESSING, (net.minecraft.world.level.block.entity.BlockEntityTicker<MachineBlockEntity>) MachineBlockEntity::serverTick,
			null, p), props(MapColor.METAL, 5.0F, SoundType.METAL).requiresCorrectToolForDrops(), true);
	public static final Block GAS_CENTRIFUGE = block("gas_centrifuge", p -> new ContainerBlock(Machines.Centrifuge::new,
			() -> ModBlockEntities.CENTRIFUGE, (net.minecraft.world.level.block.entity.BlockEntityTicker<MachineBlockEntity>) MachineBlockEntity::serverTick,
			Block.box(4, 0, 4, 12, 16, 12), p), props(MapColor.METAL, 4.0F, SoundType.METAL).noOcclusion(), true);

	// accidents
	public static final Block CORIUM = block("corium", CoriumBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(-1, 3600000).lightLevel(s -> 15).noLootTable()
					.pushReaction(PushReaction.IMMOVEABLE).randomTicks(), false);
	public static final Block SOLID_CORIUM = block("solid_corium", p -> new RadiatingBlock(100, p),
			props(MapColor.TERRACOTTA_BROWN, 40.0F, SoundType.BASALT).requiresCorrectToolForDrops().explosionResistance(600).lightLevel(s -> 3), true);
	public static final Block REACTOR_DEBRIS = block("reactor_debris", p -> new EjectaBlock(30, 0.3F, p),
			props(MapColor.COLOR_BLACK, 2.0F, SoundType.GRAVEL), true);
	public static final Block FUEL_FRAGMENT = block("fuel_fragment", p -> new EjectaBlock(50, 0.1F, p),
			props(MapColor.COLOR_GRAY, 3.0F, SoundType.METAL).requiresCorrectToolForDrops().lightLevel(s -> 4), true);

	private ModBlocks() {
	}

	public static void init() {
	}

	private static BlockBehaviour.Properties props(MapColor color, float strength, SoundType sound) {
		return BlockBehaviour.Properties.of().mapColor(color).strength(strength).sound(sound);
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties, boolean item) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Fission.id(name));
		Block b = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		if (item) {
			WITH_ITEMS.add(b);
		}
		return b;
	}
}
