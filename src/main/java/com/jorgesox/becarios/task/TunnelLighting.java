package com.jorgesox.becarios.task;

import java.util.LinkedHashMap;
import java.util.Map;

import com.jorgesox.becarios.craft.Pockets;
import com.jorgesox.becarios.craft.RecipeIndex;
import com.jorgesox.becarios.entity.BecarioEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Antorchas en los túneles, como un jugador: cuando el sitio donde está se ha
 * quedado oscuro (luz de bloque &lt; 8, que es cuando ya pueden aparecer monstruos
 * cerca), pega una antorcha en la pared a la altura de la cabeza (o en el suelo).
 * Así salen espaciadas solas (~cada 7 bloques) y le sirven de camino de vuelta.
 *
 * Si no lleva antorchas, se las fabrica en la mano (2x2) con carbón y un palo,
 * haciendo los palos y las tablas si hace falta. Sin carbón, sigue a oscuras.
 */
public final class TunnelLighting {
	/** Por debajo de esto el túnel está "oscuro". */
	private static final int DARK = 8;
	private static final int MAX_CRAFT_DEPTH = 3;

	public enum Result { LIT, NOT_DARK, NO_SPOT, NO_TORCHES }

	private TunnelLighting() {
	}

	public static Result lightIfDark(ServerLevel level, BecarioEntity becario) {
		BlockPos feet = becario.blockPosition();
		BlockPos head = feet.above();
		if (level.getBrightness(LightLayer.BLOCK, head) >= DARK || level.getBrightness(LightLayer.SKY, head) >= DARK) {
			return Result.NOT_DARK;
		}
		BlockState wallTorch = null;
		BlockPos where = null;
		// 1) En la pared, a la altura de la cabeza: no estorba y se ve de lejos.
		if (free(level, head)) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockState state = Blocks.WALL_TORCH.defaultBlockState()
					.setValue(BlockStateProperties.HORIZONTAL_FACING, d.getOpposite());
				if (state.canSurvive(level, head)) {
					wallTorch = state;
					where = head;
					break;
				}
			}
		}
		// 2) Si no, en el suelo, a sus pies.
		if (where == null && free(level, feet) && Blocks.TORCH.defaultBlockState().canSurvive(level, feet)) {
			wallTorch = Blocks.TORCH.defaultBlockState();
			where = feet;
		}
		if (where == null) {
			return Result.NO_SPOT;
		}
		if (Pockets.count(becario, s -> s.is(Items.TORCH)) == 0 && !craftTorches(level, becario)) {
			return Result.NO_TORCHES;
		}
		if (Pockets.take(becario, s -> s.is(Items.TORCH), 1).isEmpty()) {
			return Result.NO_TORCHES;
		}
		level.setBlock(where, wallTorch, Block.UPDATE_ALL);
		level.playSound(null, where, wallTorch.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
		becario.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
		return Result.LIT;
	}

	private static boolean free(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).isAir() && level.getFluidState(pos).isEmpty();
	}

	/** Fabrica antorchas en la mano. Solo lo intenta si tiene carbón (para no gastar madera en balde). */
	public static boolean craftTorches(ServerLevel level, BecarioEntity becario) {
		if (Pockets.count(becario, s -> s.is(Items.COAL) || s.is(Items.CHARCOAL)) == 0) {
			return false;
		}
		return handCraft(level, becario, Items.TORCH, 0);
	}

	/**
	 * Una fabricación en la cuadrícula 2x2 de `item`, haciendo antes (también en la
	 * mano) los ingredientes que falten: palos a partir de tablas, tablas a partir de troncos.
	 */
	private static boolean handCraft(ServerLevel level, BecarioEntity becario, Item item, int depth) {
		if (depth > MAX_CRAFT_DEPTH) {
			return false;
		}
		for (RecipeIndex.CraftRecipe recipe : RecipeIndex.of(level).craftingFor(item)) {
			if (recipe.needsTable()) {
				continue;
			}
			Map<Ingredient, Integer> grouped = new LinkedHashMap<>();
			for (Ingredient ingredient : recipe.ingredients()) {
				grouped.merge(ingredient, 1, Integer::sum);
			}
			boolean ok = true;
			for (Map.Entry<Ingredient, Integer> entry : grouped.entrySet()) {
				Ingredient ingredient = entry.getKey();
				while (ok && Pockets.count(becario, ingredient::test) < entry.getValue()) {
					boolean made = false;
					for (Item option : RecipeIndex.items(ingredient)) {
						if (option != item && handCraft(level, becario, option, depth + 1)) {
							made = true;
							break;
						}
					}
					ok = made;
				}
				if (!ok) {
					break;
				}
			}
			if (ok && Pockets.consumeOnce(becario, recipe.ingredients())) {
				becario.storeOrDrop(level, recipe.result().copy());
				return true;
			}
		}
		return false;
	}

	/** Para el resumen: ¿cuántas lleva? */
	public static int torches(BecarioEntity becario) {
		return Pockets.count(becario, (ItemStack s) -> s.is(Items.TORCH));
	}
}
