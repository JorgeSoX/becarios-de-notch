package com.jorgesox.becarios.task;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Nombres en español que el jugador puede usar en /becario mina <objetivo>.
 * Cada nombre agrupa uno o varios bloques (p. ej. "hierro" = mena de hierro
 * normal y de pizarra profunda).
 */
public final class BlockGroups {
	/**
	 * Cómo buscarlo si no está a la vista:
	 * SURFACE = explorar andando por la superficie (árboles, arena...);
	 * UNDERGROUND = bajar en escalera hasta preferredY y hacer túneles de minería.
	 */
	public enum Search { SURFACE, UNDERGROUND }

	public record Group(String label, Predicate<BlockState> test, Search search, int preferredY) {
	}

	private static final Map<String, Group> GROUPS = new LinkedHashMap<>();

	static {
		// Alturas típicas de cada mineral en el mundo normal (dónde más abunda).
		put("madera", s -> s.is(BlockTags.LOGS), Search.SURFACE, 0);
		// Piedra = la que suelta roca (cobblestone): piedra normal y de pizarra profunda.
		put("piedra", any(Blocks.STONE, Blocks.DEEPSLATE, Blocks.COBBLESTONE, Blocks.COBBLED_DEEPSLATE), Search.UNDERGROUND, 50);
		put("tierra", s -> s.is(BlockTags.DIRT), Search.SURFACE, 0);
		put("arena", s -> s.is(BlockTags.SAND), Search.SURFACE, 0);
		put("carbon", any(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE), Search.UNDERGROUND, 40);
		put("hierro", s -> s.is(BlockTags.IRON_ORES), Search.UNDERGROUND, 16);
		put("cobre", s -> s.is(BlockTags.COPPER_ORES), Search.UNDERGROUND, 48);
		put("oro", s -> s.is(BlockTags.GOLD_ORES), Search.UNDERGROUND, -16);
		put("redstone", any(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE), Search.UNDERGROUND, -56);
		put("lapislazuli", any(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE), Search.UNDERGROUND, 0);
		put("diamante", any(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE), Search.UNDERGROUND, -56);
		put("esmeralda", any(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE), Search.UNDERGROUND, 100);
		// Para el portal al Nether: obsidiana (junto a los lagos de lava de abajo) y grava (sílex).
		put("obsidiana", any(Blocks.OBSIDIAN), Search.UNDERGROUND, -54);
		put("grava", any(Blocks.GRAVEL), Search.UNDERGROUND, 30);
		// En el Nether.
		put("cuarzo", any(Blocks.NETHER_QUARTZ_ORE), Search.UNDERGROUND, 64);
		put("netherita", any(Blocks.ANCIENT_DEBRIS), Search.UNDERGROUND, 15);
	}

	private BlockGroups() {
	}

	private static void put(String name, Predicate<BlockState> test, Search search, int preferredY) {
		GROUPS.put(name, new Group(name, test, search, preferredY));
	}

	private static Predicate<BlockState> any(Block... blocks) {
		return state -> {
			for (Block block : blocks) {
				if (state.is(block)) {
					return true;
				}
			}
			return false;
		};
	}

	public static @Nullable Group get(String name) {
		return GROUPS.get(name);
	}

	public static List<String> names() {
		return List.copyOf(GROUPS.keySet());
	}
}
