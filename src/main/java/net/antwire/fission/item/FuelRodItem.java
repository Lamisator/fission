package net.antwire.fission.item;

import net.antwire.fission.nuclear.Dose;
import net.antwire.fission.nuclear.FuelData;
import net.antwire.fission.nuclear.FuelType;
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

/** A fuel assembly. Fresh it is harmless; after a stint in a reactor it is full of fission products and deadly. */
public class FuelRodItem extends Item {
	private final FuelType type;

	public FuelRodItem(FuelType type, Properties properties) {
		super(properties);
		this.type = type;
	}

	public FuelType type() {
		return this.type;
	}

	public static FuelData data(ItemStack stack) {
		FuelData d = stack.get(ModComponents.FUEL);
		return d != null ? d : FuelData.fresh(stack.getItem() instanceof FuelRodItem r ? r.type : FuelType.LEU);
	}

	public static boolean irradiated(ItemStack stack) {
		FuelData d = stack.get(ModComponents.FUEL);
		return d != null && d.irradiated();
	}

	public static boolean spent(ItemStack stack) {
		FuelData d = stack.get(ModComponents.FUEL);
		return d != null && d.spent();
	}

	@Override
	public Component getName(ItemStack stack) {
		FuelData d = stack.get(ModComponents.FUEL);
		String state = d == null || !d.irradiated() ? "" : d.spent() ? ".spent" : ".irradiated";
		return Component.translatable(this.getDescriptionId() + state);
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return irradiated(stack);
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13F * (float) Math.max(0, 1 - data(stack).fraction()));
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return 0xFF40C0FF;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
		FuelData d = data(stack);
		out.accept(Component.translatable("item.fission.fuel_rod.burnup", String.format(Locale.ROOT, "%.2f", d.burnup()),
				String.format(Locale.ROOT, "%.0f", this.type.maxBurnup)).withStyle(ChatFormatting.GRAY));
		if (d.irradiated()) {
			long now = ClientTime.now.getAsLong();
			double rads = d.radsAtOneMetre(now);
			out.accept(Component.translatable("item.fission.fuel_rod.activity", Dose.formatTbq(d.tbq(now)), Dose.format(rads))
					.withStyle(rads > 1 ? ChatFormatting.RED : ChatFormatting.GOLD));
			out.accept(Component.translatable("item.fission.fuel_rod.decay_heat", String.format(Locale.ROOT, "%.0f", d.decayWatts(now)))
					.withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
