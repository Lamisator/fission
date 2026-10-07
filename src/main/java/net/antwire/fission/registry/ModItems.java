package net.antwire.fission.registry;

import net.antwire.fission.Fission;
import net.antwire.fission.item.CanisterItem;
import net.antwire.fission.item.FuelRodItem;
import net.antwire.fission.item.LinkerItem;
import net.antwire.fission.nuclear.FuelData;
import net.antwire.fission.nuclear.FuelType;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class ModItems {
	public static final List<Item> TAB = new ArrayList<>();

	public static final Item RAW_URANIUM = item("raw_uranium", Item::new, new Item.Properties(), false);
	public static final Item YELLOWCAKE = item("yellowcake", Item::new, new Item.Properties(), true);
	public static final Item ENRICHED_URANIUM = item("enriched_uranium", Item::new, new Item.Properties(), true);
	public static final Item DEPLETED_URANIUM = item("depleted_uranium", Item::new, new Item.Properties(), true);
	public static final Item PLUTONIUM = item("plutonium", Item::new, new Item.Properties().rarity(Rarity.RARE), true);
	public static final Item ZIRCALOY_CLADDING = item("zircaloy_cladding", Item::new, new Item.Properties(), true);
	public static final Item NATURAL_FUEL_ROD = rod("natural_fuel_rod", FuelType.NATURAL);
	public static final Item LEU_FUEL_ROD = rod("leu_fuel_rod", FuelType.LEU);
	public static final Item MOX_FUEL_ROD = rod("mox_fuel_rod", FuelType.MOX);
	public static final Item ISOTOPE_CANISTER = item("isotope_canister", CanisterItem::new, new Item.Properties().stacksTo(1), false);
	public static final Item DECAYED_CANISTER = item("decayed_canister", Item::new, new Item.Properties().stacksTo(16), true);
	public static final Item CORIUM_FRAGMENT = item("corium_fragment", Item::new, new Item.Properties().rarity(Rarity.EPIC), true);
	public static final Item REACTOR_LINKER = item("reactor_linker", LinkerItem::new, new Item.Properties().stacksTo(1), true);
	public static Item GRAPHITE_DEBRIS;
	public static Item FUEL_FRAGMENT;

	static {
		for (Block b : ModBlocks.WITH_ITEMS) {
			Item it = blockItem(b);
			if (b == ModBlocks.REACTOR_DEBRIS) {
				GRAPHITE_DEBRIS = it;
			} else if (b == ModBlocks.FUEL_FRAGMENT) {
				FUEL_FRAGMENT = it;
			}
		}
	}

	public static final CreativeModeTab CREATIVE_TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Fission.id("fission"),
			FabricCreativeModeTab.builder()
					.title(Component.translatable("itemGroup.fission"))
					.icon(() -> new ItemStack(LEU_FUEL_ROD))
					.displayItems((params, output) -> TAB.forEach(output::accept))
					.build());

	private ModItems() {
	}

	public static void init() {
	}

	private static Item rod(String name, FuelType type) {
		return item(name, p -> new FuelRodItem(type, p), new Item.Properties().stacksTo(1).component(ModComponents.FUEL, FuelData.fresh(type)), true);
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties props, boolean lore) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Fission.id(name));
		if (lore) {
			props.component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.fission." + name + ".desc").withStyle(ChatFormatting.GRAY))));
		}
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
		TAB.add(item);
		return item;
	}

	private static Item blockItem(Block block) {
		var id = BuiltInRegistries.BLOCK.getKey(block);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		Item.Properties props = new Item.Properties().setId(key).useBlockDescriptionPrefix();
		props.component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("block.fission." + id.getPath() + ".desc")
				.withStyle(ChatFormatting.GRAY))));
		BlockItem item = new BlockItem(block, props);
		item.registerBlocks(Item.BY_BLOCK, item);
		Registry.register(BuiltInRegistries.ITEM, key, item);
		TAB.add(item);
		return item;
	}
}
