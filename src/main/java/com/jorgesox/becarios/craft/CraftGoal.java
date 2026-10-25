package com.jorgesox.becarios.craft;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.jorgesox.becarios.BecariosDeNotch;
import com.jorgesox.becarios.entity.BecarioEntity;
import com.jorgesox.becarios.task.BecarioTask;
import com.jorgesox.becarios.task.BlockGroups;
import com.jorgesox.becarios.task.Breaker;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ejecuta el encargo de fabricación paso a paso, como un jugador:
 * replanifica, y hace el primer paso (ir a picar/talar, fabricar en la mano,
 * poner/usar la mesa de trabajo, cargar el horno y esperar). Al acabar recoge
 * las mesas y hornos que haya puesto.
 */
public class CraftGoal extends Goal {
	private enum Phase { PLAN, GOTO, WAIT_FURNACE, PICKUP, DIG_NICHE }

	private static final double USE_RANGE_SQ = 3.5 * 3.5;
	private static final int GOTO_TIMEOUT = 400;
	private static final int STATION_SEARCH = 8;
	private static final int MAX_FAILURES = 6;

	private final BecarioEntity becario;
	private final Breaker breaker;
	private Phase phase = Phase.PLAN;
	private @Nullable BlockPos station;
	private CraftPlanner.@Nullable Step pending;
	private int wait;
	private int walkTicks;

	public CraftGoal(BecarioEntity becario) {
		this.becario = becario;
		this.breaker = new Breaker(becario);
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return becario.getCraftOrder() != null && becario.getTask() == null
			&& !becario.isOrderedToSit() && becario.getTarget() == null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		CraftOrder order = becario.getCraftOrder();
		phase = order != null && order.activeFurnace != null ? Phase.WAIT_FURNACE : Phase.PLAN;
		station = order != null ? order.activeFurnace : null;
		pending = null;
		wait = 10;
		walkTicks = 0;
	}

	@Override
	public void stop() {
		becario.getNavigation().stop();
		if (becario.level() instanceof ServerLevel level) {
			breaker.reset(level);
		}
	}

	@Override
	public void tick() {
		CraftOrder order = becario.getCraftOrder();
		if (order == null || becario.getTask() != null || !(becario.level() instanceof ServerLevel level)) {
			return; // (si acaba de salir a por materiales, este goal ya no toca)
		}
		if (wait > 0) {
			wait--;
			return;
		}
		switch (phase) {
			case PLAN -> plan(level, order);
			case GOTO -> walkAndUse(level, order);
			case WAIT_FURNACE -> waitFurnace(level, order);
			case PICKUP -> pickup(level, order);
			case DIG_NICHE -> digNiche(level, order);
		}
	}

	// ----------------------------------------------------------------- plan

	private void plan(ServerLevel level, CraftOrder order) {
		Set<Item> nearby = new java.util.HashSet<>();
		if (findStation(level, Blocks.CRAFTING_TABLE, false) != null) {
			nearby.add(Items.CRAFTING_TABLE);
		}
		if (findStation(level, Blocks.FURNACE, true) != null) {
			nearby.add(Items.FURNACE);
		}
		CraftPlanner.Plan plan = CraftPlanner.plan(level, becario, order.item, order.total, nearby);
		if (!plan.ok()) {
			becario.failCraft(Component.literal("no sé cómo conseguir ").append(name(plan.missing()))
				.append(" para fabricar ").append(name(order.item)).append(". Lo dejo."));
			return;
		}
		if (plan.steps().isEmpty()) {
			if (!order.placed.isEmpty()) {
				phase = Phase.PICKUP;
				return;
			}
			becario.completeCraft();
			return;
		}
		if (!order.announced) {
			order.announced = true;
			becario.tell(summary(order, plan.steps()));
		}
		if (order.failures >= MAX_FAILURES) {
			becario.failCraft(Component.literal("me he atascado fabricando ").append(name(order.item)).append(". Lo dejo."));
			return;
		}

		CraftPlanner.Step step = plan.steps().getFirst();
		switch (step) {
			case CraftPlanner.Gather first -> {
				if (!order.placed.isEmpty()) {
					phase = Phase.PICKUP; // recoge la mesa/horno antes de irse lejos
					return;
				}
				BlockGroups.Group group = BlockGroups.get(first.group());
				if (group == null) {
					becario.failCraft(Component.literal("no sé picar ").append(first.group()));
					return;
				}
				// Todo lo que el plan necesita de ese material, de un solo viaje.
				int blocks = 0;
				for (CraftPlanner.Step other : plan.steps()) {
					if (other instanceof CraftPlanner.Gather g && g.group().equals(first.group())) {
						blocks += g.blocks();
					}
				}
				CraftPlanner.Gather gather = new CraftPlanner.Gather(first.group(), blocks, first.item());
				// El sílex sale de la grava solo a veces: hasta 10 bloques por cada uno (para en cuanto lo tiene).
				int blocksToMine = gather.blocks() * (gather.item() == Items.FLINT ? 10 : 1);
				BecarioTask task = BecarioTask.mine(group.label(), group.test(), blocksToMine);
				task.forCraft = true;
				java.util.function.Predicate<ItemStack> counts = gatherCounter(gather.item());
				task.gatherItems = counts;
				task.gatherUntil = countMatching(counts) + gather.blocks() * perBlock(gather.item());
				becario.tell(Component.literal("me faltan " + gather.blocks() + " de " + group.label() + ". Voy a por ello."));
				becario.startTask(task);
			}
			case CraftPlanner.Craft craft -> {
				if (!craft.recipe().needsTable()) {
					doCraft(level, order, craft);
					return;
				}
				station = findStation(level, Blocks.CRAFTING_TABLE, false);
				if (station == null) {
					station = placeStation(level, order, Items.CRAFTING_TABLE, Blocks.CRAFTING_TABLE.defaultBlockState());
				}
				if (station == null && phase == Phase.DIG_NICHE) {
					return;
				}
				if (station == null) {
					stuck(order, "no encuentro sitio para la mesa de trabajo");
					return;
				}
				pending = craft;
				phase = Phase.GOTO;
				walkTicks = 0;
			}
			case CraftPlanner.Smelt smelt -> {
				station = findStation(level, Blocks.FURNACE, true);
				if (station == null) {
					station = placeStation(level, order, Items.FURNACE,
						Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facingBecario()));
				}
				if (station == null && phase == Phase.DIG_NICHE) {
					return;
				}
				if (station == null) {
					stuck(order, "no encuentro sitio para el horno");
					return;
				}
				pending = smelt;
				phase = Phase.GOTO;
				walkTicks = 0;
			}
		}
	}

	private MutableComponent summary(CraftOrder order, List<CraftPlanner.Step> steps) {
		MutableComponent text = Component.literal("para fabricar " + order.asked + "x ").append(name(order.item)).append(": ");
		List<Component> parts = new ArrayList<>();
		// Lo que hay que picar/talar, todo junto al principio (luego se hace en un solo viaje por material).
		java.util.Map<String, Integer> gather = new java.util.LinkedHashMap<>();
		for (CraftPlanner.Step step : steps) {
			if (step instanceof CraftPlanner.Gather g) {
				gather.merge(g.group(), g.blocks(), Integer::sum);
			}
		}
		gather.forEach((group, blocks) -> parts.add(Component.literal("conseguir " + blocks + " de " + group)));
		for (CraftPlanner.Step step : steps) {
			switch (step) {
				case CraftPlanner.Gather g -> { }
				case CraftPlanner.Craft c -> parts.add(Component.literal("hacer " + c.times() * c.recipe().result().getCount() + "x ")
					.append(name(c.recipe().result().getItem())));
				case CraftPlanner.Smelt s -> parts.add(Component.literal("fundir " + s.times() + "x ")
					.append(name(s.recipe().result().getItem())));
			}
		}
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				text.append(" → ");
			}
			if (i == 8 && parts.size() > 9) {
				text.append("… (" + (parts.size() - 8) + " pasos más)");
				break;
			}
			text.append(parts.get(i));
		}
		return text.append(".");
	}

	// ------------------------------------------------------- ir a la estación

	private void walkAndUse(ServerLevel level, CraftOrder order) {
		if (station == null || pending == null) {
			phase = Phase.PLAN;
			return;
		}
		Block expected = pending instanceof CraftPlanner.Smelt ? Blocks.FURNACE : Blocks.CRAFTING_TABLE;
		if (!level.getBlockState(station).is(expected)) {
			order.placed.remove(station); // la han quitado
			phase = Phase.PLAN;
			return;
		}
		Vec3 center = Vec3.atCenterOf(station);
		becario.getLookControl().setLookAt(center.x, center.y, center.z);
		if (becario.getEyePosition().distanceToSqr(center) > USE_RANGE_SQ) {
			if (walkTicks % 10 == 0) {
				becario.getNavigation().moveTo(center.x, station.getY(), center.z, 1.0);
			}
			if (++walkTicks > GOTO_TIMEOUT) {
				stuck(order, "no llego a la estación en " + station);
				phase = Phase.PLAN;
			}
			return;
		}
		becario.getNavigation().stop();
		becario.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
		switch (pending) {
			case CraftPlanner.Craft craft -> doCraft(level, order, craft);
			case CraftPlanner.Smelt smelt -> loadFurnace(level, order, smelt);
			default -> phase = Phase.PLAN;
		}
	}

	// ------------------------------------------------------------- fabricar

	private void doCraft(ServerLevel level, CraftOrder order, CraftPlanner.Craft craft) {
		becario.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
		int made = 0;
		for (int i = 0; i < craft.times(); i++) {
			if (!consumeOnce(craft.recipe().ingredients())) {
				break;
			}
			becario.storeOrDrop(level, craft.recipe().result().copy());
			made++;
		}
		if (made == 0) {
			stuck(order, "no tengo los ingredientes de " + craft.recipe().result().getItem());
		} else {
			order.failures = 0;
		}
		phase = Phase.PLAN;
		wait = 10; // un pequeño respiro entre fabricaciones, como un jugador
	}

	private boolean consumeOnce(List<Ingredient> ingredients) {
		return Pockets.consumeOnce(becario, ingredients);
	}

	private List<ItemStack> slots() {
		return Pockets.slots(becario);
	}

	private ItemStack take(java.util.function.Predicate<ItemStack> test, int count) {
		return Pockets.take(becario, test, count);
	}

	// ---------------------------------------------------------------- horno

	private void loadFurnace(ServerLevel level, CraftOrder order, CraftPlanner.Smelt smelt) {
		if (!(level.getBlockEntity(station) instanceof AbstractFurnaceBlockEntity furnace)) {
			phase = Phase.PLAN;
			return;
		}
		int times = Math.min(64, smelt.times());
		Ingredient input = smelt.recipe().input();
		ItemStack toCook = take(input::test, times);
		if (toCook.isEmpty()) {
			stuck(order, "no tengo qué meter al horno");
			phase = Phase.PLAN;
			return;
		}
		// Combustible: carbón si hay bastante; si no, tablas; si no, lo que arda.
		int cooking = toCook.getCount();
		ItemStack fuel = take(s -> s.is(Items.COAL) || s.is(Items.CHARCOAL), CraftPlanner.coalFor(cooking));
		if (fuel.isEmpty()) {
			fuel = take(s -> RecipeIndex.planks().contains(s.getItem()), CraftPlanner.planksFor(cooking));
		}
		if (fuel.isEmpty()) {
			fuel = take(s -> s.has(DataComponents.COOKING_FUEL) && !s.isDamageableItem() && !NOT_FUEL.contains(s.getItem()), CraftPlanner.planksFor(cooking));
		}
		if (fuel.isEmpty()) {
			becario.storeOrDrop(level, toCook);
			stuck(order, "no tengo combustible");
			phase = Phase.PLAN;
			return;
		}
		returnOrMerge(level, furnace, 0, toCook);
		returnOrMerge(level, furnace, 1, fuel);
		furnace.setChanged();
		order.activeFurnace = station;
		order.failures = 0;
		phase = Phase.WAIT_FURNACE;
		wait = 20;
	}

	/** Mete el stack en ese hueco del horno; si está ocupado con otra cosa, lo saca primero. */
	private void returnOrMerge(ServerLevel level, Container furnace, int slot, ItemStack stack) {
		ItemStack current = furnace.getItem(slot);
		if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, stack)) {
			becario.storeOrDrop(level, furnace.removeItemNoUpdate(slot));
			current = ItemStack.EMPTY;
		}
		if (current.isEmpty()) {
			furnace.setItem(slot, stack);
		} else {
			current.grow(stack.getCount());
		}
	}

	private void waitFurnace(ServerLevel level, CraftOrder order) {
		BlockPos pos = order.activeFurnace;
		if (pos == null || !(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) {
			order.activeFurnace = null;
			phase = Phase.PLAN;
			return;
		}
		Vec3 center = Vec3.atCenterOf(pos);
		if (becario.getEyePosition().distanceToSqr(center) > USE_RANGE_SQ) {
			if (walkTicks++ % 20 == 0) {
				becario.getNavigation().moveTo(center.x, pos.getY(), center.z, 1.0);
			}
			return;
		}
		becario.getNavigation().stop();
		becario.getLookControl().setLookAt(center.x, center.y, center.z);
		wait = 20;
		boolean inputLeft = !furnace.getItem(0).isEmpty();
		boolean lit = level.getBlockState(pos).getValueOrElse(BlockStateProperties.LIT, false);
		boolean fuelLeft = !furnace.getItem(1).isEmpty();
		if (inputLeft && (lit || fuelLeft)) {
			return; // sigue fundiendo
		}
		// Terminado (o sin combustible): recoger todo.
		becario.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
		for (int slot = 2; slot >= 0; slot--) {
			ItemStack stack = furnace.removeItemNoUpdate(slot);
			if (!stack.isEmpty()) {
				becario.storeOrDrop(level, stack);
			}
		}
		furnace.setChanged();
		order.activeFurnace = null;
		phase = Phase.PLAN;
		wait = 5;
	}

	// ------------------------------------------------------------ estaciones

	/** Una mesa/horno cercano que se pueda usar (el horno, vacío). */
	private @Nullable BlockPos findStation(ServerLevel level, Block block, boolean mustBeEmpty) {
		BlockPos origin = becario.blockPosition();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(origin.offset(-STATION_SEARCH, -3, -STATION_SEARCH), origin.offset(STATION_SEARCH, 3, STATION_SEARCH))) {
			if (!level.getBlockState(p).is(block)) {
				continue;
			}
			if (mustBeEmpty && level.getBlockEntity(p) instanceof AbstractFurnaceBlockEntity furnace
				&& (!furnace.getItem(0).isEmpty() || !furnace.getItem(2).isEmpty())) {
				continue; // es de alguien y está en uso
			}
			double d = p.distSqr(origin);
			if (d < bestDist) {
				bestDist = d;
				best = p.immutable();
			}
		}
		return best;
	}

	/** Pone la mesa/horno del inventario en un hueco con suelo, al lado del becario. */
	private @Nullable BlockPos placeStation(ServerLevel level, CraftOrder order, Item item, BlockState state) {
		ItemStack stack = take(s -> s.is(item), 1);
		if (stack.isEmpty()) {
			return null;
		}
		BlockPos feet = becario.blockPosition();
		// Primero un sitio "bonito" (con suelo); si no, cualquiera donde pueda apoyarlo,
		// como un jugador que lo pega a una pared o al techo.
		for (boolean wantFloor : new boolean[] {true, false}) {
			for (int r = 1; r <= 2; r++) {
				for (int dy : new int[] {0, -1, 1}) {
					for (Direction d : Direction.Plane.HORIZONTAL) {
						BlockPos p = feet.relative(d, r).above(dy);
						if (!canPlaceAt(level, p, wantFloor)) {
							continue;
						}
						level.setBlock(p, state, Block.UPDATE_ALL);
						level.playSound(null, p, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
						becario.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
						order.placed.add(p.immutable());
						return p.immutable();
					}
				}
			}
		}
		becario.storeOrDrop(level, stack); // no hay sitio: se la vuelve a guardar
		// En un túnel estrecho: cava un hueco en la pared para ponerla.
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos wall = feet.relative(d);
			BlockState here = level.getBlockState(wall);
			if (!here.isAir() && here.getDestroySpeed(level, wall) >= 0 && !Breaker.touchesDanger(level, wall)
				&& level.getBlockState(wall.below()).isFaceSturdy(level, wall.below(), Direction.UP)) {
				niche = wall.immutable();
				phase = Phase.DIG_NICHE;
				return null;
			}
		}
		return null;
	}

	private @Nullable BlockPos niche;

	/** Hueco libre (sin líquido ni nadie dentro) y con algún bloque al lado donde apoyarlo. */
	private boolean canPlaceAt(ServerLevel level, BlockPos p, boolean wantFloor) {
		if (!level.getBlockState(p).canBeReplaced() || !level.getFluidState(p).isEmpty()) {
			return false;
		}
		AABB box = new AABB(p);
		if (becario.getBoundingBox().intersects(box)) {
			return false;
		}
		LivingEntity owner = becario.getOwner();
		if (owner != null && owner.getBoundingBox().intersects(box)) {
			return false;
		}
		if (wantFloor) {
			return level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP);
		}
		for (Direction d : Direction.values()) {
			BlockPos n = p.relative(d);
			if (!level.getBlockState(n).canBeReplaced()) {
				return true;
			}
		}
		return false;
	}

	private void digNiche(ServerLevel level, CraftOrder order) {
		if (niche == null) {
			phase = Phase.PLAN;
			return;
		}
		becario.getLookControl().setLookAt(niche.getX() + 0.5, niche.getY() + 0.5, niche.getZ() + 0.5);
		Breaker.Result result = breaker.tick(level, niche, false);
		if (result != Breaker.Result.WORKING) {
			if (result != Breaker.Result.BROKEN) {
				stuck(order, "no puedo cavar el hueco en " + niche + " (" + result + ")");
			}
			niche = null;
			phase = Phase.PLAN;
		}
	}

	/** Un paso que no ha salido: cuenta para "me he atascado" y deja el motivo en el log. */
	private void stuck(CraftOrder order, String why) {
		order.failures++;
		wait = 40;
		BecariosDeNotch.LOGGER.info("[fabrica] {} fallo {}/{}: {} (pos={})", becario.getName().getString(),
			order.failures, MAX_FAILURES, why, becario.blockPosition());
	}

	private Direction facingBecario() {
		return becario.getDirection().getOpposite();
	}

	/** Recoge las mesas/hornos que puso él. */
	private void pickup(ServerLevel level, CraftOrder order) {
		if (order.placed.isEmpty()) {
			phase = Phase.PLAN;
			return;
		}
		BlockPos pos = order.placed.getFirst();
		BlockState state = level.getBlockState(pos);
		if (!state.is(Blocks.CRAFTING_TABLE) && !state.is(Blocks.FURNACE)) {
			order.placed.removeFirst();
			return;
		}
		if (pos.equals(order.activeFurnace)) {
			phase = Phase.WAIT_FURNACE; // aún hay algo dentro
			return;
		}
		Vec3 center = Vec3.atCenterOf(pos);
		becario.getLookControl().setLookAt(center.x, center.y, center.z);
		if (becario.getEyePosition().distanceToSqr(center) > USE_RANGE_SQ) {
			if (walkTicks++ % 10 == 0) {
				becario.getNavigation().moveTo(center.x, pos.getY(), center.z, 1.0);
			}
			if (walkTicks > GOTO_TIMEOUT) {
				order.placed.removeFirst(); // no llega: se queda ahí
				walkTicks = 0;
			}
			return;
		}
		becario.getNavigation().stop();
		// Un horno con cosas dentro: sacarlas antes de romperlo.
		if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
			for (int slot = 0; slot < 3; slot++) {
				ItemStack stack = furnace.removeItemNoUpdate(slot);
				if (!stack.isEmpty()) {
					becario.storeOrDrop(level, stack);
				}
			}
		}
		Breaker.Result result = breaker.tick(level, pos, true);
		if (result != Breaker.Result.WORKING) {
			order.placed.removeFirst();
			walkTicks = 0;
		}
	}

	/** Qué cuenta como "lo que fui a buscar": cualquier tronco si era madera, roca de cualquier tipo... */
	private static java.util.function.Predicate<ItemStack> gatherCounter(Item item) {
		if (new ItemStack(item).is(net.minecraft.tags.ItemTags.LOGS)) {
			return s -> s.is(net.minecraft.tags.ItemTags.LOGS);
		}
		if (item == Items.COBBLESTONE || item == Items.COBBLED_DEEPSLATE) {
			return s -> s.is(Items.COBBLESTONE) || s.is(Items.COBBLED_DEEPSLATE);
		}
		return s -> s.is(item);
	}

	private static int perBlock(Item item) {
		Gatherables.Source source = Gatherables.of(item);
		return source == null ? 1 : source.perBlock();
	}

	private int countMatching(java.util.function.Predicate<ItemStack> test) {
		int count = 0;
		for (ItemStack stack : slots()) {
			if (!stack.isEmpty() && test.test(stack)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static Component name(@Nullable Item item) {
		return item == null ? Component.literal("?") : new ItemStack(item).getHoverName();
	}

	/** Objetos que no hay que meter nunca al horno como combustible (por si acaso). */
	static final Set<Item> NOT_FUEL = Set.of(Items.CRAFTING_TABLE, Items.CHEST, Items.BARREL);
}
