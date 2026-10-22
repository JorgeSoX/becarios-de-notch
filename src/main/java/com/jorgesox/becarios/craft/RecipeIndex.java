package com.jorgesox.becarios.craft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;

/**
 * Índice de recetas "qué receta produce este objeto", construido a partir del
 * RecipeManager del servidor (así vale para cualquier receta del juego o de
 * otros mods/datapacks). Solo recetas normales de mesa (con forma o sin forma)
 * y de horno.
 */
public final class RecipeIndex {
	/** Una receta de mesa, ya "aplanada": ingredientes de UNA fabricación y lo que sale. */
	public record CraftRecipe(RecipeHolder<?> holder, List<Ingredient> ingredients, ItemStack result, boolean needsTable) {
	}

	/** Una receta de horno. */
	public record SmeltRecipe(RecipeHolder<?> holder, Ingredient input, ItemStack result, int cookingTime) {
	}

	private static @Nullable RecipeManager cachedFor;
	private static @Nullable RecipeIndex cached;

	private final Map<Item, List<CraftRecipe>> crafting = new HashMap<>();
	private final Map<Item, List<SmeltRecipe>> smelting = new HashMap<>();

	private RecipeIndex() {
	}

	/** El índice de este servidor (se rehace si recargan los datapacks). */
	public static synchronized RecipeIndex of(ServerLevel level) {
		RecipeManager manager = level.recipeAccess();
		if (cached == null || cachedFor != manager) {
			cached = build(manager);
			cachedFor = manager;
		}
		return cached;
	}

	@SuppressWarnings("unchecked")
	private static RecipeIndex build(RecipeManager manager) {
		RecipeIndex index = new RecipeIndex();
		for (RecipeHolder<?> holder : manager.getRecipes()) {
			Recipe<?> recipe = holder.value();
			if (recipe instanceof ShapedRecipe shaped) {
				List<Ingredient> ingredients = shaped.getIngredients().stream()
					.filter(Optional::isPresent).map(Optional::get).toList();
				ItemStack result = ((Recipe<CraftingInput>) (Recipe<?>) shaped).assemble(CraftingInput.EMPTY);
				boolean table = shaped.getWidth() > 2 || shaped.getHeight() > 2;
				index.addCraft(new CraftRecipe(holder, ingredients, result, table));
			} else if (recipe instanceof ShapelessRecipe shapeless) {
				List<Ingredient> ingredients = shapeless.placementInfo().ingredients();
				ItemStack result = ((Recipe<CraftingInput>) (Recipe<?>) shapeless).assemble(CraftingInput.EMPTY);
				index.addCraft(new CraftRecipe(holder, ingredients, result, ingredients.size() > 4));
			} else if (recipe instanceof SmeltingRecipe smelt) {
				Optional<Holder<Item>> any = smelt.input().items().findFirst();
				if (any.isEmpty()) {
					continue;
				}
				ItemStack result = smelt.assemble(new SingleRecipeInput(new ItemStack(any.get())));
				if (!result.isEmpty()) {
					index.smelting.computeIfAbsent(result.getItem(), k -> new ArrayList<>())
						.add(new SmeltRecipe(holder, smelt.input(), result, smelt.cookingTime()));
				}
			}
		}
		return index;
	}

	private void addCraft(CraftRecipe recipe) {
		if (recipe.result().isEmpty() || recipe.ingredients().isEmpty()) {
			return;
		}
		this.crafting.computeIfAbsent(recipe.result().getItem(), k -> new ArrayList<>()).add(recipe);
	}

	public List<CraftRecipe> craftingFor(Item item) {
		return this.crafting.getOrDefault(item, Collections.emptyList());
	}

	public List<SmeltRecipe> smeltingFor(Item item) {
		return this.smelting.getOrDefault(item, Collections.emptyList());
	}

	/** Los objetos que acepta un ingrediente, en orden estable. */
	public static Set<Item> items(Ingredient ingredient) {
		return ingredient.items().map(Holder::value).collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/** Todos los objetos de una etiqueta (p. ej. todas las tablas de madera). */
	public static Set<Item> tag(TagKey<Item> tag) {
		Set<Item> out = new LinkedHashSet<>();
		for (Item item : BuiltInRegistries.ITEM) {
			if (new ItemStack(item).is(tag)) {
				out.add(item);
			}
		}
		return out;
	}

	private static @Nullable Set<Item> planks;

	public static Set<Item> planks() {
		if (planks == null) {
			planks = tag(ItemTags.PLANKS);
		}
		return planks;
	}
}
