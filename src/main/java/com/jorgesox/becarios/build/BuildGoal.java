package com.jorgesox.becarios.build;

import java.util.EnumSet;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import com.jorgesox.becarios.craft.Pockets;
import com.jorgesox.becarios.craft.RecipeIndex;
import com.jorgesox.becarios.entity.BecarioEntity;
import com.jorgesox.becarios.task.BecarioTask;
import com.jorgesox.becarios.task.BlockGroups;
import com.jorgesox.becarios.task.Breaker;
import com.jorgesox.becarios.task.TunnelLighting;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Construye la casa del encargo como un jugador:
 *
 * 1. Cuenta lo que necesita y va a por lo que le falta (picar piedra, talar,
 *    hacer tablas, la puerta, la antorcha), reutilizando las órdenes de picar
 *    y los encargos de fabricación. Todo de una vez antes de empezar a poner.
 * 2. Pone bloque a bloque, andando hasta tenerlo al alcance: despeja, suelo,
 *    paredes de abajo arriba, techo, puerta y antorcha. Lo que estorba lo rompe.
 */
public class BuildGoal extends Goal {
	private static final double REACH_SQ = 4.5 * 4.5;
	private static final int WALK_TIMEOUT = 300;
	private static final int MAX_FAILURES = 6;

	private final BecarioEntity becario;
	private final Breaker breaker;
	private HousePlan.@Nullable Placement current;
	private int walkTicks;
	private int wait;

	public BuildGoal(BecarioEntity becario) {
		this.becario = becario;
		this.breaker = new Breaker(becario);
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		BuildOrder order = becario.getBuildOrder();
		// Si vuelve de picar/talar lejos, primero deshace el camino (ReturnAlongTrailGoal).
		return order != null && becario.getTask() == null && becario.getCraftOrder() == null
			&& !becario.isOrderedToSit() && becario.getTarget() == null
			&& (becario.getTrail().isEmpty() || becario.blockPosition().distSqr(order.center) < 10 * 10);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		current = null;
		wait = 5;
	}

	@Override
	public void stop() {
		becario.getNavigation().stop();
		becario.setWorkFacing(null);
		if (becario.level() instanceof ServerLevel level) {
			breaker.reset(level);
		}
	}

	@Override
	public void tick() {
		BuildOrder order = becario.getBuildOrder();
		if (order == null || becario.getTask() != null || becario.getCraftOrder() != null
			|| !(becario.level() instanceof ServerLevel level)) {
			return;
		}
		if (wait > 0) {
			wait--;
			return;
		}
		if (current == null) {
			if (!haveEverything(level, order)) {
				return; // ha salido a por material (o se ha rendido)
			}
			current = next(level, order);
			walkTicks = 0;
			if (current == null) {
				if (!order.postponed.isEmpty()) {
					// Lo que no alcanzó: otra vuelta (quizá ya se ha movido quien estorbaba).
					com.jorgesox.becarios.BecariosDeNotch.LOGGER.info("[obra] {} no pudo con: {}", becario.getName().getString(), order.postponed);
					int missing = order.postponed.size();
					order.postponed.clear();
					if (++order.failures > MAX_FAILURES) {
						becario.failBuild("no consigo terminar " + missing + " bloques de la casa (no llego o algo estorba). La dejo casi acabada.");
					}
					wait = 20;
					return;
				}
				becario.completeBuild();
				return;
			}
		}
		work(level, order, current);
	}

	// ------------------------------------------------------------ materiales

	/** Cuántos objetos de cada material faltan por poner. */
	private Map<HousePlan.Material, Integer> remaining(ServerLevel level, BuildOrder order) {
		Map<HousePlan.Material, Integer> need = new EnumMap<>(HousePlan.Material.class);
		for (HousePlan.Placement p : order.plan) {
			if (p.material() == HousePlan.Material.AIR || p.material() == HousePlan.Material.TORCH && order.noTorch) {
				continue;
			}
			if (!p.material().isDone(level.getBlockState(p.pos()))) {
				need.merge(p.material(), 1, Integer::sum);
			}
		}
		return need;
	}

	/**
	 * ¿Lleva todo lo que falta? Si no, sale a por lo primero que le falte
	 * (y devuelve false). Calcula la madera de una vez: tablas, puerta y troncos.
	 */
	private boolean haveEverything(ServerLevel level, BuildOrder order) {
		Map<HousePlan.Material, Integer> need = remaining(level, order);
		int floorNeed = need.getOrDefault(HousePlan.Material.FLOOR, 0);
		int plankNeed = need.getOrDefault(HousePlan.Material.PLANKS, 0);
		int logNeed = need.getOrDefault(HousePlan.Material.LOG, 0);
		int doorNeed = need.getOrDefault(HousePlan.Material.DOOR, 0);
		int torchNeed = need.getOrDefault(HousePlan.Material.TORCH, 0);

		int floorHave = Pockets.count(becario, HousePlan.Material.FLOOR.accepts);
		int plankHave = Pockets.count(becario, HousePlan.Material.PLANKS.accepts);
		int logHave = Pockets.count(becario, HousePlan.Material.LOG.accepts);
		int doorHave = Pockets.count(becario, HousePlan.Material.DOOR.accepts);

		int doorPlanks = doorHave < doorNeed ? 6 + (hasTable(level) ? 0 : 4) : 0;
		int planksShort = Math.max(0, plankNeed + doorPlanks - plankHave);
		int logDemand = logNeed + (planksShort + 3) / 4;

		int obsidianNeed = need.getOrDefault(HousePlan.Material.OBSIDIAN, 0);
		int fireNeed = need.getOrDefault(HousePlan.Material.FIRE, 0);
		if (!order.announced) {
			order.announced = true;
			if (order.kind == BuildOrder.Kind.PORTAL) {
				becario.tell("para el portal necesito " + obsidianNeed + " de obsidiana (con pico de diamante) y un mechero."
					+ " Voy a por lo que me falte.");
			} else {
				becario.tell("para la casa necesito " + floorNeed + " de piedra, " + plankNeed + " tablas, " + logNeed
					+ " troncos y una puerta. Voy a por lo que me falte y luego la levanto.");
			}
		}
		int obsidianHave = Pockets.count(becario, HousePlan.Material.OBSIDIAN.accepts);
		if (obsidianHave < obsidianNeed) {
			becario.startCraftForBuild(Items.OBSIDIAN, obsidianNeed - obsidianHave);
			return false;
		}
		if (fireNeed > 0 && Pockets.count(becario, HousePlan.Material.FIRE.accepts) == 0) {
			becario.startCraftForBuild(Items.FLINT_AND_STEEL, 1);
			return false;
		}
		if (floorHave < floorNeed) {
			gather(level, "piedra", HousePlan.Material.FLOOR, floorNeed - floorHave, floorNeed);
			return false;
		}
		if (logHave < logDemand) {
			gather(level, "madera", HousePlan.Material.LOG, logDemand - logHave, logDemand);
			return false;
		}
		if (planksShort > 0) {
			becario.startCraftForBuild(planksFor(level), planksShort);
			return false;
		}
		if (doorHave < doorNeed) {
			becario.startCraftForBuild(doorFor(level), 1);
			return false;
		}
		if (torchNeed > 0 && !order.noTorch && Pockets.count(becario, HousePlan.Material.TORCH.accepts) == 0
			&& !TunnelLighting.craftTorches(level, becario)) {
			order.noTorch = true;
			becario.tell("no tengo carbón para la antorcha de dentro: la casa se queda sin ella (pónsela tú si quieres).");
		}
		return true;
	}

	private void gather(ServerLevel level, String groupName, HousePlan.Material material, int blocks, int until) {
		BlockGroups.Group group = BlockGroups.get(groupName);
		if (group == null) {
			becario.failBuild("no sé conseguir " + groupName + ".");
			return;
		}
		BecarioTask task = BecarioTask.mine(group.label(), group.test(), blocks);
		task.forBuild = true;
		task.gatherItems = material.accepts;
		task.gatherUntil = until;
		becario.tell("me faltan " + blocks + " de " + group.label() + " para la casa. Voy a por ello.");
		becario.startTask(task);
	}

	private boolean hasTable(ServerLevel level) {
		if (Pockets.count(becario, s -> s.is(Items.CRAFTING_TABLE)) > 0) {
			return true;
		}
		BlockPos origin = becario.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(origin.offset(-8, -3, -8), origin.offset(8, 3, 8))) {
			if (level.getBlockState(p).is(Blocks.CRAFTING_TABLE)) {
				return true;
			}
		}
		return false;
	}

	/** Tablas del tipo de los troncos que lleva (abedul si lleva abedul...); si no, de roble. */
	private Item planksFor(ServerLevel level) {
		RecipeIndex index = RecipeIndex.of(level);
		for (ItemStack stack : Pockets.slots(becario)) {
			if (stack.isEmpty() || !stack.is(ItemTags.LOGS)) {
				continue;
			}
			for (Item plank : RecipeIndex.planks()) {
				for (RecipeIndex.CraftRecipe recipe : index.craftingFor(plank)) {
					if (recipe.ingredients().size() == 1 && recipe.ingredients().getFirst().test(stack)) {
						return plank;
					}
				}
			}
		}
		return Items.OAK_PLANKS;
	}

	/** Una puerta que pueda hacer con las tablas que lleva; si no, de roble. */
	private Item doorFor(ServerLevel level) {
		RecipeIndex index = RecipeIndex.of(level);
		Set<Item> doors = RecipeIndex.tag(ItemTags.WOODEN_DOORS);
		for (Item door : doors) {
			for (RecipeIndex.CraftRecipe recipe : index.craftingFor(door)) {
				boolean ok = true;
				for (var ingredient : recipe.ingredients()) {
					if (Pockets.count(becario, ingredient::test) == 0) {
						ok = false;
						break;
					}
				}
				if (ok) {
					return door;
				}
			}
		}
		return Items.OAK_DOOR;
	}

	// ----------------------------------------------------------------- obra

	/** El siguiente bloque por hacer (en el orden del plano). */
	private HousePlan.@Nullable Placement next(ServerLevel level, BuildOrder order) {
		for (HousePlan.Placement p : order.plan) {
			if (order.postponed.contains(p.pos())) {
				continue;
			}
			if (p.material() == HousePlan.Material.TORCH && order.noTorch) {
				continue;
			}
			if (!p.material().isDone(level.getBlockState(p.pos()))) {
				return p;
			}
		}
		return null;
	}

	private void work(ServerLevel level, BuildOrder order, HousePlan.Placement p) {
		BlockPos pos = p.pos();
		Vec3 center = Vec3.atCenterOf(pos);
		becario.getLookControl().setLookAt(center.x, center.y, center.z);
		if (becario.getEyePosition().distanceToSqr(center) > REACH_SQ) {
			// Andar hasta tenerlo a mano: para despejar, hacia el propio bloque;
			// para poner, a un sitio de dentro de la casa (nunca encima de donde va el bloque).
			Vec3 spot = p.material() == HousePlan.Material.AIR && order.kind == BuildOrder.Kind.HOUSE ? center : standingSpot(order, pos);
			walkTo(order, pos, spot);
			return;
		}
		becario.getNavigation().stop();
		becario.setWorkFacing(Direction.getApproximateNearest(center.x - becario.getX(), 0, center.z - becario.getZ()));

		BlockState here = level.getBlockState(pos);
		if (p.material() == HousePlan.Material.AIR) {
			if (breakBlock(level, order, pos)) {
				current = null;
			}
			return;
		}
		// Primero quitar lo que haya en ese sitio (tierra, hierba alta, piedra...).
		if (!here.canBeReplaced()) {
			if (breakBlock(level, order, pos)) {
				wait = 2;
			}
			return;
		}
		if (p.material() == HousePlan.Material.DOOR && !level.getBlockState(pos.above()).canBeReplaced()) {
			breakBlock(level, order, pos.above());
			return;
		}
		// Él en medio del bloque: se aparta a un sitio de dentro. Tú en medio: espera a que te quites.
		AABB box = new AABB(pos);
		if (becario.getBoundingBox().intersects(box)
			|| p.material() == HousePlan.Material.DOOR && becario.getBoundingBox().intersects(new AABB(pos.above()))) {
			walkTo(order, pos, standingSpot(order, pos.relative(order.front.getOpposite())));
			return;
		}
		LivingEntity owner = becario.getOwner();
		if (owner != null && owner.getBoundingBox().intersects(box)) {
			if (++walkTicks % 100 == 1) {
				becario.tell("apártate un poco, que tengo que poner un bloque donde estás.");
			}
			if (walkTicks > WALK_TIMEOUT) {
				order.postponed.add(pos);
				current = null;
			}
			return;
		}
		if (p.material() == HousePlan.Material.FIRE) {
			light(level, order, pos);
			return;
		}
		ItemStack item = Pockets.take(becario, p.material().accepts, 1);
		if (item.isEmpty()) {
			current = null; // se le ha acabado: vuelve a contar y va a por más
			return;
		}
		BlockState state = p.stateFor(item);
		if (!state.canSurvive(level, pos)) {
			if (p.material() == HousePlan.Material.TORCH && Blocks.TORCH.defaultBlockState().canSurvive(level, pos)) {
				state = Blocks.TORCH.defaultBlockState();
			} else if (p.material() == HousePlan.Material.TORCH || p.material() == HousePlan.Material.DOOR) {
				becario.storeOrDrop(level, item);
				order.postponed.add(pos);
				current = null;
				return;
			}
		}
		level.setBlock(pos, state, Block.UPDATE_ALL);
		if (p.material() == HousePlan.Material.DOOR) {
			level.setBlock(pos.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
		}
		level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
		becario.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
		order.failures = 0;
		current = null;
		wait = 4; // a ritmo de jugador
	}

	private void walkTo(BuildOrder order, BlockPos pos, Vec3 spot) {
		becario.setWorkFacing(null);
		if (walkTicks % 10 == 0) {
			becario.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
		}
		if (++walkTicks > WALK_TIMEOUT) {
			order.postponed.add(pos);
			current = null;
		}
	}

	/**
	 * Dónde ponerse para trabajar en ese bloque: la casilla de dentro de la casa
	 * más cercana a él (a ras del suelo). Desde ahí llega a paredes, suelo y techo.
	 */
	private static Vec3 standingSpot(BuildOrder order, BlockPos target) {
		if (order.kind == BuildOrder.Kind.PORTAL) {
			return Vec3.atBottomCenterOf(order.center.relative(order.front, 2)); // delante del portal
		}
		Direction right = order.front.getClockWise();
		int dx = target.getX() - order.center.getX();
		int dz = target.getZ() - order.center.getZ();
		int a = dx * right.getStepX() + dz * right.getStepZ();
		int b = dx * order.front.getStepX() + dz * order.front.getStepZ();
		int inner = order.size / 2 - 1;
		a = Math.max(-inner, Math.min(inner, a));
		b = Math.max(-inner, Math.min(inner, b));
		BlockPos spot = order.center.relative(right, a).relative(order.front, b);
		return Vec3.atBottomCenterOf(spot);
	}

	/** Enciende el portal con el mechero: el fuego dentro del marco lo activa (como al hacerlo un jugador). */
	private void light(ServerLevel level, BuildOrder order, BlockPos pos) {
		ItemStack lighter = null;
		for (ItemStack stack : Pockets.slots(becario)) {
			if (stack.is(Items.FLINT_AND_STEEL)) {
				lighter = stack;
				break;
			}
		}
		if (lighter == null) {
			current = null; // vuelve a contar: le falta el mechero
			return;
		}
		BlockState fire = net.minecraft.world.level.block.BaseFireBlock.getState(level, pos);
		level.setBlock(pos, fire, Block.UPDATE_ALL);
		level.playSound(null, pos, net.minecraft.sounds.SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
		becario.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
		lighter.setDamageValue(lighter.getDamageValue() + 1);
		if (lighter.getDamageValue() >= lighter.getMaxDamage()) {
			lighter.shrink(1);
		}
		if (!level.getBlockState(pos).is(Blocks.NETHER_PORTAL)) {
			order.postponed.add(pos); // el marco no estaba bien: se revisa y se reintenta
		}
		current = null;
		wait = 10;
	}

	/** Un tick rompiendo ese bloque. Devuelve true cuando ya no está. */
	private boolean breakBlock(ServerLevel level, BuildOrder order, BlockPos pos) {
		if (Breaker.touchesDanger(level, pos)) {
			order.postponed.add(pos); // no abrir paso a lava/agua
			current = null;
			return false;
		}
		Breaker.Result result = breaker.tick(level, pos, false);
		if (result == Breaker.Result.UNBREAKABLE) {
			order.postponed.add(pos);
			current = null;
			return false;
		}
		return result == Breaker.Result.BROKEN;
	}
}
