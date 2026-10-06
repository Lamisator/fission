package net.antwire.fission.item;

import net.antwire.fission.nuclear.Canister;
import net.antwire.fission.nuclear.Dose;
import net.antwire.fission.nuclear.Isotope;
import net.antwire.fission.registry.ModComponents;
import net.antwire.fission.util.ClientTime;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** A sealed stainless canister with one separated isotope from reprocessing. */
public class CanisterItem extends Item {
	public CanisterItem(Properties properties) {
		super(properties);
	}

	public static ItemStack of(net.minecraft.world.item.Item item, Isotope iso, double atoms, long time) {
		ItemStack stack = new ItemStack(item);
		stack.set(ModComponents.CANISTER, new Canister(iso, atoms, time));
		return stack;
	}

	@Override
	public Component getName(ItemStack stack) {
		Canister c = stack.get(ModComponents.CANISTER);
		return c == null ? super.getName(stack) : Component.translatable("item.fission.isotope_canister.of", c.isotope().label);
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return stack.has(ModComponents.CANISTER);
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		Canister c = stack.get(ModComponents.CANISTER);
		if (c == null) return 0;
		double left = c.atoms(ClientTime.now.getAsLong()) / Math.max(1e-30, c.atoms());
		return Math.round(13F * (float) Math.clamp(left, 0, 1));
	}

	@Override
	public int getBarColor(ItemStack stack) {
		Canister c = stack.get(ModComponents.CANISTER);
		return c != null && c.isotope().shortLived() ? 0xFF50C8FF : 0xFFFFB030;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
		Canister c = stack.get(ModComponents.CANISTER);
		if (c == null) {
			return;
		}
		long now = ClientTime.now.getAsLong();
		out.accept(Component.translatable("item.fission.isotope_canister.half_life", c.isotope().halfLifeText()).withStyle(ChatFormatting.GRAY));
		double rads = c.radsAtOneMetre(now);
		out.accept(Component.translatable("item.fission.isotope_canister.activity", Dose.formatTbq(c.tbq(now)), Dose.format(rads))
				.withStyle(rads > 1 ? ChatFormatting.RED : ChatFormatting.GOLD));
		out.accept(Component.translatable(c.isotope().shortLived() ? "item.fission.isotope_canister.short" : "item.fission.isotope_canister.long")
				.withStyle(ChatFormatting.DARK_AQUA));
	}
}
