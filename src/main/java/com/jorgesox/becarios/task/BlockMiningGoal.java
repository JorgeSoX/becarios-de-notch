package com.jorgesox.becarios.task;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jorgesox.becarios.entity.BecarioEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Minar/talar: busca el bloque objetivo más cercano al que pueda llegar
 * andando, camina hasta él, lo rompe a la velocidad que le correspondería a un
 * jugador con esa herramienta, y guarda los drops en su inventario.
 *
 * Si no ve ninguno, deja paso a ExpeditionGoal (explorar o excavar túneles).
 */
public class BlockMiningGoal extends Goal {
	private static final int SEARCH_RADIUS = 24;
	private static final int SEARCH_HEIGHT = 12;
	private static final double REACH_SQ = 4.2 * 4.2;
	private static final int MAX_STUCK_TICKS = 160;
	private static final int IGNORE_TICKS = 600;
	private static final int MAX_PATH_TESTS = 8;

	private final BecarioEntity becario;
	/** Bloques descartados (inalcanzables o peligrosos) hasta cierto tick. */
	private final Map<BlockPos, Long> ignored = new HashMap<>();

	private BlockPos target;
	private int breakTicks;
	private int ticksNeeded;
	private int stuckTicks;
	private int failedSearches;
	private long nextSearchTick;

	public BlockMiningGoal(BecarioEntity becario) {
		this.becario = becario;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		BecarioTask task = becario.getTask();
		return task != null && task.kind == BecarioTask.Kind.MINE && !task.needsExpedition && !becario.isOrderedToSit();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		target = null;
		breakTicks = 0;
		stuckTicks = 0;
		failedSearches = 0;
		nextSearchTick = 0;
		ignored.clear();
	}

	@Override
	public void stop() {
		clearProgress();
		target = null;
		becario.getNavigation().stop();
	}

	@Override
	public void tick() {
		BecarioTask task = becario.getTask();
		if (task == null || task.blocks == null) {
			return;
		}
		ServerLevel level = (ServerLevel) becario.level();

		if (task.done >= task.target || becario.gatheredEnough(task)) {
			becario.finishTask("listo: " + task.done + " de " + task.label + ".");
			return;
		}

		// El objetivo pudo cambiar (otro jugador lo rompió, etc.)
		if (target != null && !task.blocks.test(level.getBlockState(target))) {
			clearProgress();
			target = null;
		}

		if (target == null) {
			if (level.getGameTime() < nextSearchTick) {
				return;
			}
			target = findTarget(level, task);
			if (target == null) {
				nextSearchTick = level.getGameTime() + 20;
				if (++failedSearches >= 3) {
					// Nada a la vista: a explorar / excavar (ExpeditionGoal). No se rinde:
					// sigue hasta conseguirlo o hasta que le digan /becario para.
					failedSearches = 0;
					if (task.expeditions == 0) {
						becario.tell("no veo " + task.label + " cerca. Voy a buscarlo, no paro hasta conseguirlo.");
					}
					task.needsExpedition = true;
				}
				return;
			}
			failedSearches = 0;
			breakTicks = 0;
			stuckTicks = 0;
		}

		Vec3 center = Vec3.atCenterOf(target);
		becario.getLookControl().setLookAt(center.x, center.y, center.z);

		if (becario.getEyePosition().distanceToSqr(center) > REACH_SQ) {
			// Aún lejos: caminar (recalculando la ruta cada medio segundo).
			if (stuckTicks % 10 == 0) {
				becario.getNavigation().moveTo(center.x, center.y, center.z, 1.0);
			}
			if (++stuckTicks > MAX_STUCK_TICKS) {
				ignore(level, target);
				clearProgress();
				target = null;
			}
			return;
		}

		becario.getNavigation().stop();
		mineStep(level, task);
	}

	private void mineStep(ServerLevel level, BecarioTask task) {
		BlockState state = level.getBlockState(target);

		if (breakTicks == 0) {
			if (!becario.equipBestToolFor(state)) {
				becario.needToolFor(state, "necesito una herramienta adecuada para " + task.label
					+ ". Dame un pico o un hacha (Mayús + clic derecho sobre mí con ella).");
				return;
			}
			ticksNeeded = computeBreakTicks(level, state);
		}

		breakTicks++;
		if (breakTicks % 5 == 1) {
			becario.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
		}
		level.destroyBlockProgress(becario.getId(), target, Math.min(9, breakTicks * 10 / ticksNeeded));

		if (breakTicks >= ticksNeeded) {
			breakBlock(level, task, state);
		}
	}

	private void breakBlock(ServerLevel level, BecarioTask task, BlockState state) {
		ItemStack tool = becario.getMainHandItem();
		List<ItemStack> drops = Block.getDrops(state, level, target, level.getBlockEntity(target), becario, tool);

		level.destroyBlock(target, false, becario, 512);
		level.destroyBlockProgress(becario.getId(), target, -1);

		for (ItemStack drop : drops) {
			becario.storeOrDrop(level, drop);
		}
		if (!tool.isEmpty() && tool.isDamageableItem()) {
			tool.hurtAndBreak(1, becario, EquipmentSlot.MAINHAND);
		}

		task.done++;
		replantIfStump(level, target, state);
		target = null;
		breakTicks = 0;
	}

	/**
	 * Si acaba de cortar la base de un tronco (debajo hay tierra) y lleva
	 * brotes, planta uno: así el bosque no se acaba.
	 */
	private void replantIfStump(ServerLevel level, BlockPos pos, BlockState broken) {
		if (!broken.is(BlockTags.LOGS) || !level.getBlockState(pos.below()).is(BlockTags.DIRT) || !level.getBlockState(pos).isAir()) {
			return;
		}
		for (int slot = 0; slot < becario.getInventory().getContainerSize(); slot++) {
			ItemStack stack = becario.getInventory().getItem(slot);
			if (stack.is(ItemTags.SAPLINGS) && stack.getItem() instanceof BlockItem blockItem) {
				BlockState sapling = blockItem.getBlock().defaultBlockState();
				if (sapling.canSurvive(level, pos)) {
					level.setBlockAndUpdate(pos, sapling);
					stack.shrink(1);
				}
				return;
			}
		}
	}

	/** Misma fórmula que el jugador: dureza * (30 con herramienta correcta, 100 sin ella) / velocidad. */
	private int computeBreakTicks(ServerLevel level, BlockState state) {
		float hardness = state.getDestroySpeed(level, target);
		ItemStack tool = becario.getMainHandItem();
		float speed = Math.max(0.1F, tool.getDestroySpeed(state));
		boolean correct = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
		return Mth.clamp(Mth.ceil(hardness * (correct ? 30 : 100) / speed), 4, 600);
	}

	private void clearProgress() {
		if (target != null && becario.level() instanceof ServerLevel level) {
			level.destroyBlockProgress(becario.getId(), target, -1);
		}
		breakTicks = 0;
	}

	private void ignore(ServerLevel level, BlockPos pos) {
		ignored.put(pos, level.getGameTime() + IGNORE_TICKS);
	}

	/** El bloque objetivo más cercano al que exista ruta andando, o null. */
	private BlockPos findTarget(ServerLevel level, BecarioTask task) {
		long now = level.getGameTime();
		ignored.values().removeIf(until -> until < now);

		BlockPos origin = becario.blockPosition();
		// Minerales: solo los que están a la vista (con aire al lado). Los enterrados
		// los encuentra ExpeditionGoal cavando, como haría un jugador.
		BlockGroups.Group group = BlockGroups.get(task.label);
		boolean onlyExposed = group != null && group.search() == BlockGroups.Search.UNDERGROUND;
		List<BlockPos> candidates = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(
			origin.offset(-SEARCH_RADIUS, -SEARCH_HEIGHT, -SEARCH_RADIUS),
			origin.offset(SEARCH_RADIUS, SEARCH_HEIGHT, SEARCH_RADIUS))) {
			if (!task.blocks.test(level.getBlockState(p))) {
				continue;
			}
			BlockPos pos = p.immutable();
			if (!ignored.containsKey(pos) && isSafe(level, pos) && (!onlyExposed || isExposed(level, pos))) {
				candidates.add(pos);
			}
		}
		candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));

		int tested = 0;
		for (BlockPos pos : candidates) {
			if (tested++ >= MAX_PATH_TESTS) {
				break;
			}
			Path path = becario.getNavigation().createPath(pos, 1);
			if (isUsablePath(path, pos)) {
				return pos;
			}
			ignore(level, pos);
		}
		return null;
	}

	/**
	 * Un bloque sólido nunca es "alcanzable" como casilla, así que canReach()
	 * suele ser false aunque se pueda llegar a su lado. Vale cualquier ruta cuyo
	 * último nodo quede a distancia de brazo del bloque.
	 */
	private boolean isUsablePath(Path path, BlockPos pos) {
		if (path == null) {
			return false;
		}
		if (path.canReach()) {
			return true;
		}
		Node end = path.getEndNode();
		return end != null && end.asBlockPos().distSqr(pos) <= 3.5 * 3.5;
	}

	private static boolean isExposed(ServerLevel level, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			if (level.getBlockState(pos.relative(direction)).isAir()) {
				return true;
			}
		}
		return false;
	}

	/** No rompe el suelo que pisa ni bloques pegados a agua/lava (evita inundaciones y caídas). */
	private boolean isSafe(ServerLevel level, BlockPos pos) {
		if (pos.equals(becario.blockPosition().below())) {
			return false;
		}
		for (Direction direction : Direction.values()) {
			if (!level.getFluidState(pos.relative(direction)).isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
