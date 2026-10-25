package com.jorgesox.becarios.craft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jorgesox.becarios.entity.BecarioEntity;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Planificador "de jugador": para tener N de un objeto, mira qué lleva encima
 * y decide la cadena de pasos (picar/talar materias primas, fabricar en la
 * cuadrícula 2x2 o en la mesa, fundir en el horno), incluidas las herramientas
 * y estaciones que hagan falta (pico adecuado, mesa de trabajo, horno, combustible).
 *
 * Se recalcula desde cero antes de cada paso con el inventario real, así que si
 * algo sale distinto de lo previsto (tala abedul en vez de roble, le das cosas...)
 * el plan se adapta solo.
 */
public final class CraftPlanner {
	public sealed interface Step permits Gather, Craft, Smelt {
	}

	/** Picar/talar `blocks` bloques del grupo. */
	public record Gather(String group, int blocks, Item item) implements Step {
	}

	/** Fabricar `times` veces la receta (en la mano o en mesa). */
	public record Craft(RecipeIndex.CraftRecipe recipe, int times) implements Step {
	}

	/** Fundir `times` objetos en el horno. */
	public record Smelt(RecipeIndex.SmeltRecipe recipe, int times) implements Step {
	}

	/** Resultado: los pasos en orden, o el objeto que no sabe conseguir. */
	public record Plan(List<Step> steps, @Nullable Item missing) {
		public boolean ok() {
			return missing == null;
		}
	}

	private static final int MAX_DEPTH = 10;
	private static final int MAX_NODES = 6000;
	private static final Set<Item> COAL = Set.of(Items.COAL, Items.CHARCOAL);

	private final RecipeIndex index;
	private Map<Item, Integer> have;
	private List<Step> steps = new ArrayList<>();
	private int nodes;
	private @Nullable Item missing;

	private CraftPlanner(RecipeIndex index, Map<Item, Integer> have) {
		this.index = index;
		this.have = have;
	}

	/** Plan para llegar a tener `total` unidades de `item` (contando lo que ya lleva). */
	public static Plan plan(ServerLevel level, BecarioEntity becario, Item item, int total) {
		return plan(level, becario, item, total, Set.of());
	}

	/**
	 * Igual, contando además las estaciones (mesa, horno) que ya hay puestas cerca:
	 * no hace falta fabricar otra mientras esa siga ahí.
	 */
	public static Plan plan(ServerLevel level, BecarioEntity becario, Item item, int total, Set<Item> stationsNearby) {
		Map<Item, Integer> have = snapshot(becario);
		for (Item station : stationsNearby) {
			have.merge(station, 1, Integer::sum);
		}
		CraftPlanner planner = new CraftPlanner(RecipeIndex.of(level), have);
		boolean ok = planner.need(Set.of(item), total, new HashSet<>(), 0);
		return new Plan(planner.steps, ok ? null : (planner.missing != null ? planner.missing : item));
	}

	/** Lo que lleva encima: inventario + manos. */
	public static Map<Item, Integer> snapshot(BecarioEntity becario) {
		Map<Item, Integer> have = new LinkedHashMap<>();
		for (int slot = 0; slot < becario.getInventory().getContainerSize(); slot++) {
			add(have, becario.getInventory().getItem(slot));
		}
		add(have, becario.getItemBySlot(EquipmentSlot.MAINHAND));
		add(have, becario.getItemBySlot(EquipmentSlot.OFFHAND));
		return have;
	}

	private static void add(Map<Item, Integer> have, ItemStack stack) {
		if (!stack.isEmpty()) {
			have.merge(stack.getItem(), stack.getCount(), Integer::sum);
		}
	}

	// ------------------------------------------------------------- búsqueda

	/**
	 * Consigue `count` unidades de cualquiera de `accept` (consumiéndolas de la
	 * simulación). Añade los pasos necesarios. Devuelve false si no hay forma.
	 */
	private boolean need(Set<Item> accept, int count, Set<Item> path, int depth) {
		if (++nodes > MAX_NODES) {
			return false;
		}
		// 1) Lo que ya tiene. Las herramientas (objetos con durabilidad) no se
		//    gastan como ingrediente salvo que sean justo lo que se pide.
		int remaining = count;
		for (Item item : accept) {
			if (remaining <= 0) {
				break;
			}
			if (depth > 0 && new ItemStack(item).isDamageableItem()) {
				continue;
			}
			int available = have.getOrDefault(item, 0);
			int take = Math.min(available, remaining);
			if (take > 0) {
				have.put(item, available - take);
				remaining -= take;
			}
		}
		if (remaining <= 0) {
			return true;
		}
		if (depth > MAX_DEPTH) {
			return false;
		}

		// 2) Producirlo: probar opciones de la más directa a la más rebuscada.
		for (Option option : options(accept, path)) {
			Map<Item, Integer> savedHave = new HashMap<>(have);
			List<Step> savedSteps = new ArrayList<>(steps);
			if (option.produce(remaining, path, depth)) {
				return true;
			}
			have = savedHave;
			steps = savedSteps;
		}
		if (missing == null || depth == 0) {
			missing = accept.iterator().next();
		}
		return false;
	}

	/**
	 * Necesita tener (sin gastarla) una herramienta o estación: mesa, horno, pico...
	 * Si no la tiene, la fabrica y la "devuelve" a la simulación.
	 */
	private boolean ensure(Item tool, Set<Item> path, int depth) {
		if (have.getOrDefault(tool, 0) > 0) {
			return true;
		}
		if (path.contains(tool) || !need(Set.of(tool), 1, path, depth + 1)) {
			return false;
		}
		have.merge(tool, 1, Integer::sum);
		return true;
	}

	/** ¿Tiene ya algo que saque este bloque? Si no, se fabrica el pico más básico que sirva. */
	private boolean ensurePickaxeFor(BlockState sample, Set<Item> path, int depth) {
		for (Map.Entry<Item, Integer> entry : have.entrySet()) {
			if (entry.getValue() > 0 && new ItemStack(entry.getKey()).isCorrectToolForDrops(sample)) {
				return true;
			}
		}
		Item pick = Gatherables.basicPickaxeFor(sample);
		return pick != null && ensure(pick, path, depth);
	}

	private interface Option {
		boolean produce(int remaining, Set<Item> path, int depth);
	}

	private List<Option> options(Set<Item> accept, Set<Item> path) {
		List<Option> direct = new ArrayList<>();
		List<Option> ready = new ArrayList<>();
		List<Option> other = new ArrayList<>();
		for (Item item : accept) {
			if (path.contains(item)) {
				continue; // evita vueltas (lingote -> bloque -> lingote...)
			}
			Gatherables.Source source = Gatherables.of(item);
			if (source != null) {
				direct.add((remaining, p, depth) -> gather(item, source, remaining, p, depth));
			}
			for (RecipeIndex.CraftRecipe recipe : index.craftingFor(item)) {
				Option option = (remaining, p, depth) -> craft(item, recipe, remaining, p, depth);
				(allInHand(recipe.ingredients()) ? ready : other).add(option);
			}
			for (RecipeIndex.SmeltRecipe recipe : index.smeltingFor(item)) {
				Option option = (remaining, p, depth) -> smelt(item, recipe, remaining, p, depth);
				(allInHand(List.of(recipe.input())) ? ready : other).add(option);
			}
		}
		List<Option> all = new ArrayList<>(direct);
		all.addAll(ready);
		all.addAll(other);
		return all;
	}

	private boolean allInHand(List<Ingredient> ingredients) {
		for (Ingredient ingredient : ingredients) {
			boolean found = false;
			for (Item item : RecipeIndex.items(ingredient)) {
				if (have.getOrDefault(item, 0) > 0) {
					found = true;
					break;
				}
			}
			if (!found) {
				return false;
			}
		}
		return true;
	}

	private boolean gather(Item item, Gatherables.Source source, int remaining, Set<Item> path, int depth) {
		Set<Item> next = with(path, item);
		if (source.sample() != null && !ensurePickaxeFor(source.sample(), next, depth)) {
			return false;
		}
		int blocks = (remaining + source.perBlock() - 1) / source.perBlock();
		steps.add(new Gather(source.group(), blocks, item));
		have.merge(item, blocks * source.perBlock() - remaining, Integer::sum);
		return true;
	}

	private boolean craft(Item item, RecipeIndex.CraftRecipe recipe, int remaining, Set<Item> path, int depth) {
		int perCraft = Math.max(1, recipe.result().getCount());
		int times = (remaining + perCraft - 1) / perCraft;
		Set<Item> next = with(path, item);
		if (recipe.needsTable() && !ensure(Items.CRAFTING_TABLE, next, depth)) {
			return false;
		}
		// Ingredientes iguales juntos (p. ej. 3 tablas para un pico).
		Map<Ingredient, Integer> grouped = new LinkedHashMap<>();
		for (Ingredient ingredient : recipe.ingredients()) {
			grouped.merge(ingredient, 1, Integer::sum);
		}
		for (Map.Entry<Ingredient, Integer> entry : grouped.entrySet()) {
			if (!need(RecipeIndex.items(entry.getKey()), entry.getValue() * times, next, depth + 1)) {
				return false;
			}
		}
		steps.add(new Craft(recipe, times));
		have.merge(item, times * perCraft - remaining, Integer::sum);
		return true;
	}

	private boolean smelt(Item item, RecipeIndex.SmeltRecipe recipe, int remaining, Set<Item> path, int depth) {
		int perSmelt = Math.max(1, recipe.result().getCount());
		int times = (remaining + perSmelt - 1) / perSmelt;
		Set<Item> next = with(path, item);
		if (!ensure(Items.FURNACE, next, depth)) {
			return false;
		}
		if (!need(RecipeIndex.items(recipe.input()), times, next, depth + 1)) {
			return false;
		}
		if (!fuel(times, next, depth)) {
			return false;
		}
		steps.add(new Smelt(recipe, times));
		have.merge(item, times * perSmelt - remaining, Integer::sum);
		return true;
	}

	/** Combustible: carbón si lo tiene (1 = 8 objetos); si no, tablas (1 = 1,5 objetos). */
	private boolean fuel(int items, Set<Item> path, int depth) {
		int coalNeeded = (items + 7) / 8;
		int coal = 0;
		for (Item c : COAL) {
			coal += have.getOrDefault(c, 0);
		}
		if (coal >= coalNeeded) {
			return need(COAL, coalNeeded, path, depth + 1);
		}
		return need(RecipeIndex.planks(), Math.max(1, (items * 2 + 2) / 3), path, depth + 1);
	}

	private static Set<Item> with(Set<Item> path, Item item) {
		Set<Item> next = new HashSet<>(path);
		next.add(item);
		return next;
	}

	/** Combustible que hay que meter al horno para `items` objetos, a partir de lo que lleva. */
	public static int coalFor(int items) {
		return (items + 7) / 8;
	}

	public static int planksFor(int items) {
		return Math.max(1, (items * 2 + 2) / 3);
	}
}
