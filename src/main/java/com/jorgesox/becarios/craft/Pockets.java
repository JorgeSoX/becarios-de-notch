package com.jorgesox.becarios.craft;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.jorgesox.becarios.entity.BecarioEntity;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * "Los bolsillos" del becario: inventario + mano izquierda + mano derecha.
 * Lo mismo que cuenta el planificador (CraftPlanner#snapshot), para que lo que
 * planifica y lo que luego gasta coincidan siempre.
 */
public final class Pockets {
	private Pockets() {
	}

	/** Todos los huecos, en este orden: inventario, mano izquierda, mano derecha. */
	public static List<ItemStack> slots(BecarioEntity becario) {
		List<ItemStack> stacks = new ArrayList<>();
		for (int slot = 0; slot < becario.getInventory().getContainerSize(); slot++) {
			stacks.add(becario.getInventory().getItem(slot));
		}
		stacks.add(becario.getItemBySlot(EquipmentSlot.OFFHAND));
		stacks.add(becario.getItemBySlot(EquipmentSlot.MAINHAND));
		return stacks;
	}

	public static int count(BecarioEntity becario, Predicate<ItemStack> test) {
		int count = 0;
		for (ItemStack stack : slots(becario)) {
			if (!stack.isEmpty() && test.test(stack)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/**
	 * Quita un objeto para cada ingrediente (una fabricación). Todo o nada.
	 * Las herramientas (objetos con durabilidad) solo se gastan si no hay otra cosa que valga.
	 */
	public static boolean consumeOnce(BecarioEntity becario, List<Ingredient> ingredients) {
		List<ItemStack> stacks = slots(becario);
		int[] left = new int[stacks.size()];
		for (int i = 0; i < stacks.size(); i++) {
			left[i] = stacks.get(i).getCount();
		}
		int[] chosen = new int[ingredients.size()];
		for (int n = 0; n < ingredients.size(); n++) {
			Ingredient ingredient = ingredients.get(n);
			int pick = -1;
			for (int pass = 0; pass < 2 && pick < 0; pass++) {
				for (int i = 0; i < stacks.size(); i++) {
					ItemStack stack = stacks.get(i);
					if (left[i] > 0 && ingredient.test(stack) && (pass == 1 || !stack.isDamageableItem())) {
						pick = i;
						break;
					}
				}
			}
			if (pick < 0) {
				return false;
			}
			left[pick]--;
			chosen[n] = pick;
		}
		for (int index : chosen) {
			stacks.get(index).shrink(1);
		}
		becario.getInventory().setChanged();
		return true;
	}

	/** Saca hasta `count` objetos iguales que cumplan la condición. */
	public static ItemStack take(BecarioEntity becario, Predicate<ItemStack> test, int count) {
		ItemStack out = ItemStack.EMPTY;
		for (ItemStack stack : slots(becario)) {
			if (count <= 0) {
				break;
			}
			if (stack.isEmpty() || !test.test(stack) || (!out.isEmpty() && !ItemStack.isSameItemSameComponents(out, stack))) {
				continue;
			}
			int n = Math.min(count, stack.getCount());
			if (out.isEmpty()) {
				out = stack.copyWithCount(n);
			} else {
				out.grow(n);
			}
			stack.shrink(n);
			count -= n;
		}
		becario.getInventory().setChanged();
		return out;
	}
}
