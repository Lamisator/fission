package net.antwire.fission.castor;

import net.antwire.fission.block.entity.StorageBlockEntity;
import net.antwire.fission.registry.ModCastor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A disposal cell of a final repository: waste sealed in a copper canister, packed in bentonite clay, deep in rock.
 * It takes everything that goes to permanent storage, cooled spent fuel included (direct disposal), and lets
 * practically nothing out. The gantry crane fills it from a CASTOR; isotope pipes deliver to it too.
 */
public class FinalRepositoryBlockEntity extends StorageBlockEntity {
	public FinalRepositoryBlockEntity(BlockPos pos, BlockState state) {
		super(ModCastor.FINAL_REPOSITORY_BE, pos, state, 18);
	}

	@Override
	public boolean allowed(ItemStack stack) {
		return Permanent.accepts(stack, this.level == null ? 0 : this.level.getGameTime());
	}

	@Override
	protected double leak() {
		return 1.0e-6;
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.fission.final_repository");
	}
}
