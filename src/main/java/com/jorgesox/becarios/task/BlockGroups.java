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
	public record Group(String label, Predicate<BlockState> test) {
	}

	private static final Map<String, Group> GROUPS = new LinkedHashMap<>();

	static {
		put("madera", s -> s.is(BlockTags.LOGS));
		put("piedra", s -> s.is(BlockTags.BASE_STONE_OVERWORLD));
		put("tierra", s -> s.is(BlockTags.DIRT));
		put("arena", s -> s.is(BlockTags.SAND));
		put("carbon", any(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE));
		put("hierro", s -> s.is(BlockTags.IRON_ORES));
		put("cobre", s -> s.is(BlockTags.COPPER_ORES));
		put("oro", s -> s.is(BlockTags.GOLD_ORES));
		put("redstone", any(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE));
		put("lapislazuli", any(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE));
		put("diamante", any(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE));
		put("esmeralda", any(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE));
	}

	private BlockGroups() {
	}

	private static void put(String name, Predicate<BlockState> test) {
		GROUPS.put(name, new Group(name, test));
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
