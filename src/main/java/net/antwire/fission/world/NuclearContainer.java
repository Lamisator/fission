package net.antwire.fission.world;

import net.minecraft.world.item.ItemStack;

/** A block that tubes can deliver to. */
public interface NuclearContainer {
	/** Whether it takes this stack (one item of it) from a tube right now. */
	boolean acceptsFromTube(ItemStack stack);

	/** Takes one item of the stack; only called after {@link #acceptsFromTube}. */
	void insertFromTube(ItemStack one);
}
