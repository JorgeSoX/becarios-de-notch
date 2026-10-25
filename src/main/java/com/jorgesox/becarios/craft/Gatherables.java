package com.jorgesox.becarios.craft;

import org.jspecify.annotations.Nullable;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Materias primas que el becario sabe conseguir del mundo picando o talando:
 * a qué grupo de bloques (/becario mina) corresponden, cuántas suelta cada
 * bloque más o menos y qué bloque "de muestra" usar para saber qué pico hace falta.
 */
public final class Gatherables {
	/**
	 * @param group     nombre del grupo en BlockGroups
	 * @param perBlock  cuántas suelta cada bloque (aprox., a la baja)
	 * @param sample    bloque de muestra para comprobar la herramienta (null = no hace falta)
	 */
	public record Source(String group, int perBlock, @Nullable BlockState sample) {
	}

	private Gatherables() {
	}

	public static @Nullable Source of(Item item) {
		if (new ItemStack(item).is(ItemTags.LOGS)) {
			return new Source("madera", 1, null);
		}
		if (item == Items.COBBLESTONE || item == Items.COBBLED_DEEPSLATE) {
			return new Source("piedra", 1, Blocks.STONE.defaultBlockState());
		}
		if (item == Items.COAL) {
			return new Source("carbon", 1, Blocks.COAL_ORE.defaultBlockState());
		}
		if (item == Items.RAW_IRON) {
			return new Source("hierro", 1, Blocks.IRON_ORE.defaultBlockState());
		}
		if (item == Items.RAW_COPPER) {
			return new Source("cobre", 2, Blocks.COPPER_ORE.defaultBlockState());
		}
		if (item == Items.RAW_GOLD) {
			return new Source("oro", 1, Blocks.GOLD_ORE.defaultBlockState());
		}
		if (item == Items.DIAMOND) {
			return new Source("diamante", 1, Blocks.DIAMOND_ORE.defaultBlockState());
		}
		if (item == Items.REDSTONE) {
			return new Source("redstone", 4, Blocks.REDSTONE_ORE.defaultBlockState());
		}
		if (item == Items.LAPIS_LAZULI) {
			return new Source("lapislazuli", 4, Blocks.LAPIS_ORE.defaultBlockState());
		}
		if (item == Items.EMERALD) {
			return new Source("esmeralda", 1, Blocks.EMERALD_ORE.defaultBlockState());
		}
		if (item == Items.OBSIDIAN) {
			return new Source("obsidiana", 1, Blocks.OBSIDIAN.defaultBlockState());
		}
		if (item == Items.FLINT) {
			// Sale de la grava de vez en cuando: se pica grava hasta que salga (ver CraftGoal).
			return new Source("grava", 1, null);
		}
		if (item == Items.QUARTZ) {
			return new Source("cuarzo", 1, Blocks.NETHER_QUARTZ_ORE.defaultBlockState());
		}
		if (item == Items.ANCIENT_DEBRIS) {
			return new Source("netherita", 1, Blocks.ANCIENT_DEBRIS.defaultBlockState());
		}
		if (item == Items.SAND) {
			return new Source("arena", 1, null);
		}
		if (item == Items.DIRT) {
			return new Source("tierra", 1, null);
		}
		return null;
	}

	/** El pico más básico que sirve para sacar algo de este bloque (null si ni el de diamante vale). */
	public static @Nullable Item basicPickaxeFor(BlockState state) {
		for (Item pick : new Item[] {Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE}) {
			if (new ItemStack(pick).isCorrectToolForDrops(state)) {
				return pick;
			}
		}
		return null;
	}
}
