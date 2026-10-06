package net.antwire.fission.item;

import net.antwire.fission.block.entity.LinkedBlockEntity;
import net.antwire.fission.block.entity.ReactorControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Data cable reel: use it on a reactor controller, then on a console, SCRAM button, annunciator or core map to wire them up. */
public class LinkerItem extends Item {
	public LinkerItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		ItemStack stack = context.getItemInHand();
		BlockPos pos = context.getClickedPos();
		if (level.isClientSide() || player == null) {
			return InteractionResult.SUCCESS;
		}
		var be = level.getBlockEntity(pos);
		if (be instanceof ReactorControllerBlockEntity) {
			CompoundTag tag = new CompoundTag();
			tag.putLong("reactor", pos.asLong());
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			player.sendOverlayMessage(Component.translatable("item.fission.reactor_linker.picked", pos.toShortString()));
			return InteractionResult.SUCCESS;
		}
		if (be instanceof LinkedBlockEntity linked) {
			CustomData data = stack.get(DataComponents.CUSTOM_DATA);
			long reactor = data == null ? Long.MIN_VALUE : data.copyTag().getLongOr("reactor", Long.MIN_VALUE);
			if (reactor == Long.MIN_VALUE) {
				player.sendOverlayMessage(Component.translatable("item.fission.reactor_linker.first"));
				return InteractionResult.FAIL;
			}
			linked.link(BlockPos.of(reactor));
			player.sendOverlayMessage(Component.translatable("item.fission.reactor_linker.linked", BlockPos.of(reactor).toShortString()));
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}
}
