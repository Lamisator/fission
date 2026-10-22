package net.antwire.fission.registry;

import net.antwire.fission.Fission;
import net.antwire.fission.block.ContainerBlock;
import net.antwire.fission.block.entity.StorageBlockEntity;
import net.antwire.fission.castor.CastorWagon;
import net.antwire.fission.castor.FinalRepositoryBlockEntity;
import net.antwire.fission.castor.GantryCraneBlock;
import net.antwire.fission.castor.GantryCraneBlockEntity;
import net.antwire.fission.castor.ShuntingLocomotive;
import net.antwire.fission.castor.StationRailBlock;
import net.antwire.gridworks.api.GridworksApi;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.List;
import java.util.function.Function;

/** The CASTOR transport chain: casks on wagons, the shunter, station rails, the gantry crane and the final repository. */
public final class ModCastor {
	// blocks shown on the wagons (no items)
	public static final Block CASTOR_CASK = block("castor_cask", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(50, 1200).sound(SoundType.METAL).noOcclusion().noLootTable(), false);
	public static final Block SHUNTER_BODY = block("shunter_body", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(5).sound(SoundType.METAL).noOcclusion().noLootTable(), false);

	public static final Block STATION_RAIL = block("station_rail", StationRailBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.POWERED_RAIL), true);
	public static final Block GANTRY_CRANE = block("gantry_crane", GantryCraneBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(5).sound(SoundType.METAL).requiresCorrectToolForDrops(), true);
	public static final Block FINAL_REPOSITORY = block("final_repository", p -> new ContainerBlock(FinalRepositoryBlockEntity::new,
			() -> ModCastor.FINAL_REPOSITORY_BE, (BlockEntityTicker<StorageBlockEntity>) StorageBlockEntity::serverTick, null, p),
			BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(8, 1200).sound(SoundType.STONE).requiresCorrectToolForDrops(), true);

	public static final BlockEntityType<GantryCraneBlockEntity> GANTRY_CRANE_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Fission.id("gantry_crane"), FabricBlockEntityTypeBuilder.create(GantryCraneBlockEntity::new, GANTRY_CRANE).build());
	public static final BlockEntityType<FinalRepositoryBlockEntity> FINAL_REPOSITORY_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Fission.id("final_repository"), FabricBlockEntityTypeBuilder.create(FinalRepositoryBlockEntity::new, FINAL_REPOSITORY).build());

	public static final EntityType<CastorWagon> CASTOR_WAGON = entity("castor_wagon",
			EntityType.Builder.<CastorWagon>of(CastorWagon::new, MobCategory.MISC).noLootTable().sized(0.98F, 0.7F).clientTrackingRange(8));
	public static final EntityType<ShuntingLocomotive> SHUNTING_LOCOMOTIVE = entity("shunting_locomotive",
			EntityType.Builder.<ShuntingLocomotive>of(ShuntingLocomotive::new, MobCategory.MISC).noLootTable().sized(0.98F, 0.7F).clientTrackingRange(8));

	public static final Item CASTOR_WAGON_ITEM = item("castor_wagon", p -> new MinecartItem(CASTOR_WAGON, p), new Item.Properties().stacksTo(1));
	public static final Item SHUNTING_LOCOMOTIVE_ITEM = item("shunting_locomotive", p -> new MinecartItem(SHUNTING_LOCOMOTIVE, p),
			new Item.Properties().stacksTo(1));

	private ModCastor() {
	}

	public static void init() {
		for (Block b : List.of(STATION_RAIL, GANTRY_CRANE, FINAL_REPOSITORY)) {
			blockItem(b);
		}
		GridworksApi.DEVICE.registerForBlockEntity((be, side) -> be, GANTRY_CRANE_BE);
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties, boolean item) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Fission.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> entity(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Fission.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Fission.id(name));
		props.component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.fission." + name + ".desc").withStyle(ChatFormatting.GRAY))));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
		ModItems.TAB.add(item);
		return item;
	}

	private static void blockItem(Block block) {
		var id = BuiltInRegistries.BLOCK.getKey(block);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		Item.Properties props = new Item.Properties().setId(key).useBlockDescriptionPrefix();
		props.component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("block.fission." + id.getPath() + ".desc")
				.withStyle(ChatFormatting.GRAY))));
		BlockItem item = new BlockItem(block, props);
		item.registerBlocks(Item.BY_BLOCK, item);
		Registry.register(BuiltInRegistries.ITEM, key, item);
		ModItems.TAB.add(item);
	}
}
