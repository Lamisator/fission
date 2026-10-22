package net.antwire.fission.world;

import dev.radiation.api.RadiationApi;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.antwire.fission.Fission;
import net.antwire.fission.nuclear.Canister;
import net.antwire.fission.nuclear.Dose;
import net.antwire.fission.nuclear.FuelData;
import net.antwire.fission.registry.ModComponents;
import net.antwire.fission.registry.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Fission products where they do not belong. Spent fuel, active isotope canisters, plutonium and the debris of a core
 * cannot be got rid of by throwing them into fire or lava, onto a cactus or into an explosion, and they do not despawn:
 * they have to go into a holding basin, a storage drum or a CASTOR. Lying on the ground, or kept in a chest, a barrel, a
 * furnace or any other container not made for them, they radiate freely and seep into the ground (or the water) under
 * them: the patch there becomes contaminated, up to a share of what the items give off, and stays so after they are
 * gone - until it is washed off with Radiation's decontamination sprayer.
 */
public final class LooseFission {
	public static final TagKey<DamageType> RESISTS = TagKey.create(Registries.DAMAGE_TYPE, Fission.id("fission_products_resist"));
	private static final int UPDATE_TICKS = 40;
	/** Each second the patch under loose fission products takes up this share of their dose rate at one metre ... */
	private static final double UPTAKE_PER_SECOND = 2e-5;
	/** ... up to this share of it. */
	private static final double UPTAKE_CAP = 0.005;
	/** Chunks looked through for containers per tick. */
	private static final int CHUNKS_PER_TICK = 32;

	private record Spot(ServerLevel level, BlockPos pos) {
	}

	private static final Map<ServerLevel, LongOpenHashSet> LOADED = new HashMap<>();
	private static final Map<ServerLevel, LongArrayList> TO_SCAN = new HashMap<>();
	/** Containers (not made for them) with fission products in them: key -> where. */
	private static final Map<String, Spot> CONTAINERS = new HashMap<>();

	private LooseFission() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item) {
				guard(item, level.getGameTime());
			} else if (entity instanceof ServerPlayer player) {
				repair(player.getInventory(), level);
				repair(player.getEnderChestInventory(), level);
			}
		});
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			LOADED.computeIfAbsent(level, l -> new LongOpenHashSet()).add(chunk.getPos().pack());
			TO_SCAN.computeIfAbsent(level, l -> new LongArrayList()).add(chunk.getPos().pack());
		});
		ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
			LongOpenHashSet set = LOADED.get(level);
			if (set != null) set.remove(chunk.getPos().pack());
		});
		ServerTickEvents.END_SERVER_TICK.register(LooseFission::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			TO_SCAN.clear();
			CONTAINERS.clear();
		});
	}

	/** Fission products that must be stored: irradiated fuel, active canisters, plutonium, core debris. */
	public static boolean isProduct(ItemStack stack, long now) {
		if (stack.isEmpty()) return false;
		FuelData fuel = stack.get(ModComponents.FUEL);
		if (fuel != null) return fuel.irradiated();
		Canister can = stack.get(ModComponents.CANISTER);
		if (can != null) return !can.decayed(now);
		return stack.is(ModItems.PLUTONIUM) || stack.is(ModItems.CORIUM_FRAGMENT) || stack.is(ModItems.FUEL_FRAGMENT)
				|| ModItems.GRAPHITE_DEBRIS != null && stack.is(ModItems.GRAPHITE_DEBRIS);
	}

	/** An item entity of fission products: neither fire nor lava nor anything else destroys it, and it never despawns. */
	private static void guard(ItemEntity item, long now) {
		ItemStack stack = item.getItem();
		if (!isProduct(stack, now)) return;
		DamageResistant had = stack.get(DataComponents.DAMAGE_RESISTANT);
		if (had == null || named(had)) {
			HolderSet<DamageType> types = resists(item.level());
			if (types != null) {
				ItemStack copy = stack.copy();
				copy.set(DataComponents.DAMAGE_RESISTANT, new DamageResistant(types));
				item.setItem(copy);
			}
		}
		item.setUnlimitedLifetime();
	}

	/**
	 * The damage types fission products resist, as a plain list: clients do not resolve the tag, so an item naming it
	 * cannot be decoded there and kicks the player the moment it comes into view.
	 */
	private static HolderSet<DamageType> resists(Level level) {
		var tag = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(RESISTS);
		return tag.map(t -> HolderSet.direct(t.stream().toList())).orElse(null);
	}

	/** Made by 1.5.0: names the tag. */
	private static boolean named(DamageResistant resistant) {
		return resistant.types().unwrapKey().filter(RESISTS::equals).isPresent();
	}

	/** Swaps the tag in stacks made by 1.5.0 for the plain list. */
	private static void repair(Container c, Level level) {
		HolderSet<DamageType> types = null;
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			DamageResistant had = s.get(DataComponents.DAMAGE_RESISTANT);
			if (had == null || !named(had)) continue;
			if (types == null && (types = resists(level)) == null) return;
			s.set(DataComponents.DAMAGE_RESISTANT, new DamageResistant(types));
		}
	}

	private static void tick(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			scanSome(level);
		}
		if (server.getTickCount() % UPDATE_TICKS != 0) return;
		float seconds = UPDATE_TICKS / 20f;
		for (ServerLevel level : server.getAllLevels()) {
			long now = level.getGameTime();
			for (Entity e : level.getAllEntities()) {
				if (e instanceof ItemEntity item && item.isAlive()) {
					ItemStack stack = item.getItem();
					if (!isProduct(stack, now)) continue;
					guard(item, now);
					radiate(level, "fission_item_" + item.getStringUUID(), item.position().add(0, 0.25, 0), item.blockPosition(),
							Dose.radsAtOneMetre(stack, now), seconds);
				} else if (e instanceof ServerPlayer player) {
					repair(player.getInventory(), level);
				} else if (e instanceof Container c && e.isAlive()) {
					repair(c, level);
					if (ours(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace())) continue;
					double dose = productDose(c, now);
					if (dose > 0) {
						radiate(level, "fission_cart_" + e.getStringUUID(), e.position().add(0, 0.5, 0), e.blockPosition(), dose, seconds);
					}
				}
			}
		}
		for (Iterator<Map.Entry<String, Spot>> it = CONTAINERS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<String, Spot> entry = it.next();
			Spot spot = entry.getValue();
			ServerLevel level = spot.level();
			if (!level.isLoaded(spot.pos())) {
				it.remove();
				continue;
			}
			double dose = level.getBlockEntity(spot.pos()) instanceof Container c ? productDose(c, level.getGameTime()) : 0;
			if (dose <= 0) {
				RadiationApi.endPulse(entry.getKey());
				it.remove();
				continue;
			}
			radiate(level, entry.getKey(), Vec3.atCenterOf(spot.pos()), spot.pos(), dose, seconds);
		}
	}

	/** Looks through a few loaded chunks for containers holding fission products (all of them over a few seconds). */
	private static void scanSome(ServerLevel level) {
		LongArrayList queue = TO_SCAN.get(level);
		if (queue == null) return;
		if (queue.isEmpty()) {
			LongOpenHashSet loaded = LOADED.get(level);
			if (loaded == null || loaded.isEmpty() || level.getGameTime() % 200 != 0) return;
			queue.addAll(loaded);
		}
		long now = level.getGameTime();
		for (int n = 0; n < CHUNKS_PER_TICK && !queue.isEmpty(); n++) {
			long key = queue.removeLong(queue.size() - 1);
			LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
			if (chunk == null) continue;
			for (BlockEntity be : chunk.getBlockEntities().values()) {
				if (!(be instanceof Container c) || be.isRemoved()) continue;
				repair(c, level);
				if (ours(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).getNamespace())) continue;
				if (productDose(c, now) > 0) {
					String k = "fission_store_" + level.dimension().identifier() + "_" + be.getBlockPos().toShortString().replace(", ", "_");
					CONTAINERS.putIfAbsent(k, new Spot(level, be.getBlockPos().immutable()));
				}
			}
		}
	}

	/** Fission's and Radiation's own stores, machines and casks are made for them. */
	private static boolean ours(String namespace) {
		return namespace.equals(Fission.MOD_ID) || namespace.equals("radiation");
	}

	private static double productDose(Container c, long now) {
		double dose = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			if (isProduct(s, now)) dose += Dose.radsAtOneMetre(s, now);
		}
		return dose;
	}

	/** Free radiation from the spot, and the patch of ground (or water) under it taking some of it up. */
	private static void radiate(ServerLevel level, String key, Vec3 at, BlockPos ground, double dose, float seconds) {
		RadiationApi.pulse(level, key, at, (float) dose, RadiationApi.radiusFor((float) dose), UPDATE_TICKS * 2);
		double cap = dose * UPTAKE_CAP;
		float have = RadiationApi.contaminationAt(level, ground);
		if (have < cap) {
			RadiationApi.contaminate(level, ground, (float) Math.min(cap - have, dose * UPTAKE_PER_SECOND * seconds));
		}
	}
}
