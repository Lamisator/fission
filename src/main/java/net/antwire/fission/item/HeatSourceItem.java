package net.antwire.fission.item;

import net.antwire.fission.nuclear.HeatSource;
import net.antwire.fission.registry.ModComponents;
import net.antwire.fission.util.ClientTime;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Plutonium-238 oxide pellets in iridium cladding, fuel for the radioisotope generator. It starts decaying when it first
 * goes into a generator (the generator stamps the date on it).
 */
public class HeatSourceItem extends Item {
	public HeatSourceItem(Properties properties) {
		super(properties);
	}

	/** Electrical watts this heat source gives in a generator at {@code now}; an unstamped one is fresh. */
	public static double watts(ItemStack stack, long now) {
		HeatSource h = stack.get(ModComponents.HEAT_SOURCE);
		return h == null ? HeatSource.FRESH_WATTS : h.watts(now);
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return stack.has(ModComponents.HEAT_SOURCE);
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13F * (float) (watts(stack, ClientTime.now.getAsLong()) / HeatSource.FRESH_WATTS));
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return 0xFFFF8030;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
		double w = watts(stack, ClientTime.now.getAsLong());
		out.accept(Component.translatable("item.fission.pu238_heat_source.output", String.format(Locale.ROOT, "%.2f", w),
				String.format(Locale.ROOT, "%.2f", 100 * w / HeatSource.FRESH_WATTS)).withStyle(ChatFormatting.GOLD));
		out.accept(Component.translatable("item.fission.pu238_heat_source.half_life", String.format(Locale.ROOT, "%.0f", HeatSource.halfLifeRealDays()))
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
