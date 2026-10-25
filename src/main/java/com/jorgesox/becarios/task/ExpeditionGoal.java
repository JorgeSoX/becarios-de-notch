package com.jorgesox.becarios.task;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import com.jorgesox.becarios.BecariosDeNotch;
import com.jorgesox.becarios.entity.BecarioEntity;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * "Buscarse la vida": cuando no hay nada de lo pedido a la vista.
 *
 * - SUPERFICIE (madera, arena, tierra): camina en una dirección para explorar,
 *   cada vez más lejos y girando en cada intento, y vuelve a mirar.
 * - SUBTERRÁNEO (minerales, piedra): baja en escalera hasta la altura donde más
 *   abunda ese mineral y hace túneles de 1x2. Mientras excava vigila las paredes:
 *   si ve el mineral a menos de 5 bloques, cava hasta él y lo saca.
 *
 * El estado (dirección, túnel...) se conserva entre intentos de la misma orden,
 * así que sigue cavando donde lo dejó. Cada paso queda en su rastro
 * (BecarioEntity#addTrail) para poder volver luego por el mismo camino.
 *
 * Lava: si un bloque a romper o el hueco de destino toca lava, la tapa con un
 * bloque de su inventario (piedra, tierra...) y sigue. Si no tiene con qué,
 * prueba otra dirección. No se rinde: sigue hasta cumplir la orden (o /becario para).
 */
public class ExpeditionGoal extends Goal {
	private static final int REPORT_EVERY = 1200;          // avisar del progreso cada minuto
	private static final int SURFACE_TIMEOUT = 600;        // 30 s andando por intento
	private static final int STEPS_BETWEEN_LOOKS = 12;     // cada 12 pasos, mirar alrededor
	private static final int TUNNEL_SEGMENT = 24;          // bloques por tramo antes de girar
	private static final int STEP_TIMEOUT = 80;
	private static final int ORE_SCAN = 4;
	private static final double REACH_SQ = 4.5 * 4.5;
	/** Máximo intentando llegar a un mineral concreto antes de darlo por imposible. */
	private static final int ORE_TIMEOUT = 1200;

	private final BecarioEntity becario;
	private final Breaker breaker;

	// Estado que se conserva entre intentos de la MISMA orden.
	private @Nullable BecarioTask stateFor;
	private Direction dir = Direction.NORTH;
	private int segment;
	private int failedDirections;
	/** Pasos que debe avanzar en horizontal para salir de un atasco antes de volver a bajar. */
	private int escapeSteps;
	private final Set<BlockPos> badOres = new HashSet<>();
	/** Ya ha avisado de que no tiene antorchas (una vez por orden). */
	private boolean warnedNoTorches;

	// Estado de este intento.
	private int ticks;
	private int stepsThisRun;
	private @Nullable BlockPos surfaceGoal;
	private @Nullable BlockPos stepTarget;
	private int stepTicks;
	private @Nullable BlockPos oreTarget;
	private int oreTicks;

	public ExpeditionGoal(BecarioEntity becario) {
		this.becario = becario;
		this.breaker = new Breaker(becario);
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		BecarioTask task = becario.getTask();
		return task != null && task.kind == BecarioTask.Kind.MINE && task.needsExpedition
			&& !becario.isOrderedToSit() && becario.getTarget() == null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		BecarioTask task = becario.getTask();
		if (task != stateFor) {
			// Orden nueva: empezar de cero, hacia donde mira.
			stateFor = task;
			dir = becario.getDirection().getAxis().isHorizontal() ? becario.getDirection() : Direction.NORTH;
			segment = 0;
			failedDirections = 0;
			escapeSteps = 0;
			badOres.clear();
			warnedNoTorches = false;
			becario.clearTrail();
		} else if (isSurface(task)) {
			dir = dir.getClockWise(); // en superficie, cada intento hacia otro lado
		}
		task.expeditions++;
		ticks = 0;
		stepsThisRun = 0;
		surfaceGoal = null;
		stepTarget = null;
		oreTarget = null;
	}

	@Override
	public void stop() {
		if (becario.level() instanceof ServerLevel level) {
			breaker.reset(level);
		}
		becario.setWorkFacing(null);
		becario.getNavigation().stop();
	}

	private static boolean isSurface(BecarioTask task) {
		BlockGroups.Group group = BlockGroups.get(task.label);
		return group == null || group.search() == BlockGroups.Search.SURFACE;
	}

	private void debug(String what) {
		BecariosDeNotch.LOGGER.debug("[expedicion] {} pos={} dir={}", what, becario.blockPosition(), dir);
	}

	/** Pausa la expedición: el BlockMiningGoal vuelve a mirar alrededor (y luego seguimos). */
	private void pauseToLook() {
		debug("pausa para mirar (pasos=" + stepsThisRun + ", escape=" + escapeSteps + ")");
		BecarioTask task = becario.getTask();
		if (task != null) {
			task.needsExpedition = false;
		}
	}

	@Override
	public void tick() {
		BecarioTask task = becario.getTask();
		if (task == null || task.blocks == null || !(becario.level() instanceof ServerLevel level)) {
			return;
		}
		if (task.done >= task.target || becario.gatheredEnough(task)) {
			becario.finishTask("listo: " + task.done + " de " + task.label + ". Vuelvo contigo.");
			return;
		}
		if (!becario.hasInventorySpace()) {
			becario.failTask("tengo el inventario lleno. Llevo " + task.done + " de " + task.label + ". Vuelvo contigo.");
			return;
		}
		ticks++;
		task.searchTicks++;
		if (task.searchTicks % REPORT_EVERY == 0) {
			becario.tell("sigo buscando " + task.label + " (llevo " + task.done + "/" + task.target
				+ ", estoy a altura " + becario.getBlockY() + ").");
		}
		if (isSurface(task)) {
			becario.setWorkFacing(null);
			tickSurface(level, task);
		} else {
			tickUnderground(level, task);
			becario.setWorkFacing(dir);
		}
	}

	// ------------------------------------------------------------ superficie

	private void tickSurface(ServerLevel level, BecarioTask task) {
		if (surfaceGoal == null) {
			int distance = 32 + 16 * Math.min(6, task.expeditions / 4);
			BlockPos from = becario.blockPosition().relative(dir, distance);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, from.getX(), from.getZ());
			surfaceGoal = new BlockPos(from.getX(), y, from.getZ());
		}
		if (ticks % 20 == 1) {
			becario.getNavigation().moveTo(surfaceGoal.getX() + 0.5, surfaceGoal.getY(), surfaceGoal.getZ() + 0.5, 1.0);
		}
		if (ticks % 40 == 0) {
			becario.addTrail(becario.blockPosition());
		}
		boolean arrived = becario.blockPosition().distSqr(surfaceGoal) < 25;
		if (arrived || ticks > SURFACE_TIMEOUT) {
			pauseToLook();
		}
	}

	// ----------------------------------------------------------- subterráneo

	private void tickUnderground(ServerLevel level, BecarioTask task) {
		BlockGroups.Group group = BlockGroups.get(task.label);
		if (ticks % 100 == 0) {
			debug("latido step=" + stepTarget + " stepTicks=" + stepTicks + " ore=" + oreTarget
				+ " pasos=" + stepsThisRun + " escape=" + escapeSteps + " fallos=" + failedDirections
				+ " nav=" + becario.getNavigation().isDone() + " agua=" + becario.isInWater()
				+ " suelo=" + becario.onGround() + " exacta=" + becario.position());
		}
		// 1) ¿Hay mineral a la vista en las paredes? Prioridad: sacarlo.
		if (oreTarget == null && stepTarget == null && ticks % 10 == 0) {
			oreTarget = findNearbyOre(level, task);
			oreTicks = 0;
		}
		if (oreTarget != null) {
			digToOre(level, task);
			return;
		}

		// 2) Cada cierto número de pasos, dejar que el buscador normal mire alrededor
		//    (cuevas, menas expuestas a más distancia).
		if (stepsThisRun >= STEPS_BETWEEN_LOOKS) {
			pauseToLook();
			return;
		}

		// 3) Avanzar: escalera hacia abajo o túnel horizontal.
		int targetY = Math.max(level.getMinY() + 6, group.preferredY());
		boolean descending = becario.getBlockY() > targetY && escapeSteps <= 0;
		advance(level, descending);
	}

	/** Mineral del tipo pedido a menos de ORE_SCAN bloques (aunque esté dentro de la roca). */
	private @Nullable BlockPos findNearbyOre(ServerLevel level, BecarioTask task) {
		BlockPos origin = becario.blockPosition();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(origin.offset(-ORE_SCAN, -ORE_SCAN, -ORE_SCAN), origin.offset(ORE_SCAN, ORE_SCAN + 1, ORE_SCAN))) {
			if (!task.blocks.test(level.getBlockState(p)) || badOres.contains(p)) {
				continue;
			}
			double d = p.distSqr(origin);
			if (d < bestDist) {
				bestDist = d;
				best = p.immutable();
			}
		}
		return best;
	}

	/**
	 * Cava en línea recta (desde los ojos) hasta el mineral: rompe lo que haya
	 * en medio, como haría un jugador, y después el mineral.
	 */
	private void digToOre(ServerLevel level, BecarioTask task) {
		BlockPos ore = oreTarget;
		if (!task.blocks.test(level.getBlockState(ore))) {
			oreTarget = null; // ya no está
			return;
		}
		Vec3 eye = becario.getEyePosition();
		Vec3 center = Vec3.atCenterOf(ore);
		becario.getLookControl().setLookAt(center.x, center.y, center.z);

		if (eye.distanceToSqr(center) > REACH_SQ) {
			// Fuera de alcance: acercarse cavando en su dirección.
			// Si está casi en la vertical del mineral, se mantiene la dirección
			// (si no, oscila este/oeste sin avanzar) y baja en escalera.
			double dx = center.x - becario.getX();
			double dz = center.z - becario.getZ();
			if (dx * dx + dz * dz >= 4.0) {
				Direction towards = Direction.getApproximateNearest(dx, 0, dz);
				if (towards.getAxis().isHorizontal()) {
					dir = towards;
				}
			}
			boolean below = ore.getY() < becario.getBlockY() - 1;
			boolean above = ore.getY() > becario.getBlockY() + 2;
			if (++oreTicks > ORE_TIMEOUT) {
				debug("no llego al mineral " + ore);
				badOres.add(ore);
				oreTarget = null;
				return;
			}
			if (above) {
				// Encima y fuera de alcance: lo dejamos (subir cavando es lento y peligroso).
				badOres.add(ore);
				oreTarget = null;
				return;
			}
			advance(level, below);
			return;
		}

		BlockHitResult hit = level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, becario));
		BlockPos next = hit.getType() == HitResult.Type.MISS ? ore : hit.getBlockPos();
		if (!makeSafe(level, next)) {
			badOres.add(ore); // lava y nada con qué taparla
			oreTarget = null;
			return;
		}
		Breaker.Result result = breaker.tick(level, next, next.equals(ore));
		if (result == Breaker.Result.NO_TOOL) {
			becario.needToolFor(level.getBlockState(next), "necesito un pico adecuado para " + task.label
				+ ". Dámelo (Mayús + clic derecho sobre mí con él).");
		} else if (result == Breaker.Result.UNBREAKABLE) {
			badOres.add(ore);
			oreTarget = null;
		} else if (result == Breaker.Result.BROKEN && next.equals(ore)) {
			task.done++;
			oreTarget = null;
		}
	}

	/**
	 * Antes de romper un bloque: si toca lava (o agua por encima), tapa esos
	 * huecos con bloques del inventario. Devuelve false si no ha podido.
	 */
	private boolean makeSafe(ServerLevel level, BlockPos pos) {
		if (!Breaker.touchesDanger(level, pos)) {
			return true;
		}
		for (Direction d : Direction.values()) {
			BlockPos side = pos.relative(d);
			boolean lava = level.getFluidState(side).is(net.minecraft.tags.FluidTags.LAVA);
			boolean waterAbove = d == Direction.UP && level.getFluidState(side).is(net.minecraft.tags.FluidTags.WATER);
			if ((lava || waterAbove) && !becario.placeFillerAt(level, side)) {
				return false;
			}
		}
		if (level.getFluidState(pos).is(net.minecraft.tags.FluidTags.LAVA) && !becario.placeFillerAt(level, pos)) {
			return false;
		}
		return !Breaker.touchesDanger(level, pos);
	}

	/**
	 * Un paso de excavación en la dirección actual.
	 * Escalera: despeja el bloque de delante-abajo y 2 por encima, y baja.
	 * Túnel: despeja delante (pies y cabeza) y avanza.
	 */
	private void advance(ServerLevel level, boolean descending) {
		// ¿Estamos entrando en el hueco ya despejado?
		if (stepTarget != null) {
			Vec3 pos = becario.position();
			boolean arrived = Math.abs(pos.x - (stepTarget.getX() + 0.5)) < 0.45
				&& Math.abs(pos.z - (stepTarget.getZ() + 0.5)) < 0.45
				&& Math.abs(pos.y - stepTarget.getY()) < (becario.isInWater() ? 1.5 : 0.6);
			if (arrived) {
				becario.addTrail(stepTarget);
				stepTarget = null;
				lightTunnel(level);
				stepsThisRun++;
				failedDirections = 0;
				if (escapeSteps > 0) {
					escapeSteps--;
				}
				if (!descending && ++segment >= TUNNEL_SEGMENT) {
					segment = 0;
					dir = dir.getClockWise(); // túnel en "U" para cubrir más zona
				}
				return;
			}
			if (++stepTicks > STEP_TIMEOUT) {
				debug("no llegó al paso " + stepTarget);
				stepTarget = null;
				turn();
				return;
			}
			if (stepTicks % 10 == 1) {
				becario.getNavigation().moveTo(stepTarget.getX() + 0.5, stepTarget.getY(), stepTarget.getZ() + 0.5, 1.0);
			}
			if (becario.getNavigation().isDone()) {
				becario.getMoveControl().setWantedPosition(stepTarget.getX() + 0.5, stepTarget.getY(), stepTarget.getZ() + 0.5, 1.0);
			}
			return;
		}

		BlockPos feet = becario.blockPosition();
		BlockPos next = descending ? feet.relative(dir).below() : feet.relative(dir);
		BlockPos[] toClear = descending
			? new BlockPos[] {next.above(2), next.above(), next}
			: new BlockPos[] {next.above(), next};

		for (BlockPos pos : toClear) {
			BlockState state = level.getBlockState(pos);
			boolean lavaHere = level.getFluidState(pos).is(net.minecraft.tags.FluidTags.LAVA);
			if (isPassable(level, pos, state) && !lavaHere) {
				continue; // aire o agua: se puede pasar (nadando si hace falta)
			}
			if (lavaHere) {
				if (!becario.placeFillerAt(level, pos)) {
					debug("lava sin bloques para taparla en " + pos);
					turn();
				}
				return; // tapada: en el siguiente tick se rompe/pasa
			}
			if (!makeSafe(level, pos)) {
				debug("lava sin bloques para taparla junto a " + pos);
				turn();
				return;
			}
			becario.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
			Breaker.Result result = breaker.tick(level, pos, false);
			if (result == Breaker.Result.UNBREAKABLE) {
				debug("irrompible " + pos);
				turn();
			}
			return; // un bloque cada vez
		}

		// Todo despejado: comprobar el suelo (y tapar lava/agujeros si hace falta).
		if (!prepareLanding(level, next)) {
			debug("aterrizaje inseguro " + next);
			turn();
			return;
		}
		stepTarget = next;
		stepTicks = 0;
	}

	/** Antorcha si se ha quedado a oscuras (avisa una vez si no tiene con qué). */
	private void lightTunnel(ServerLevel level) {
		TunnelLighting.Result result = TunnelLighting.lightIfDark(level, becario);
		if (result == TunnelLighting.Result.LIT) {
			debug("antorcha");
		} else if (result == TunnelLighting.Result.NO_TORCHES && !warnedNoTorches) {
			warnedNoTorches = true;
			becario.tell("no tengo antorchas ni carbón para hacerlas. Sigo a oscuras; si me das carbón, voy alumbrando.");
		}
	}

	/** Esta dirección no vale: probar la siguiente. Si ninguna vale, mirar en otra zona. */
	private void turn() {
		dir = dir.getClockWise();
		if (++failedDirections >= 4) {
			// Ninguna dirección vale desde aquí: avanzar en horizontal unos pasos
			// (otra zona) antes de volver a intentar bajar.
			failedDirections = 0;
			escapeSteps = 8;
			pauseToLook();
		}
	}

	private static boolean isPassable(ServerLevel level, BlockPos pos, BlockState state) {
		return state.isAir() || state.getCollisionShape(level, pos).isEmpty();
	}

	/**
	 * Suelo seguro para el hueco: si debajo hay lava o un agujero, pone un
	 * bloque para tener suelo. Si no tiene bloques y la caída es grande o hay
	 * líquido, devuelve false.
	 */
	private boolean prepareLanding(ServerLevel level, BlockPos pos) {
		BlockPos floor = pos.below();
		boolean floorIsLava = level.getFluidState(floor).is(net.minecraft.tags.FluidTags.LAVA);
		boolean floorIsWater = level.getFluidState(floor).is(net.minecraft.tags.FluidTags.WATER);
		boolean floorIsHole = isPassable(level, floor, level.getBlockState(floor));
		if (floorIsLava || floorIsHole) {
			if (becario.placeFillerAt(level, floor)) {
				return true;
			}
			if (floorIsLava) {
				return false;
			}
			if (floorIsWater) {
				return true; // agua: se cruza nadando
			}
			// Sin bloques: se acepta una caída corta (hasta 3) sin líquido debajo.
			for (int i = 2; i <= 3; i++) {
				BlockPos below = pos.below(i);
				if (level.getFluidState(below).is(net.minecraft.tags.FluidTags.LAVA)) {
					return false;
				}
				if (level.getFluidState(below).is(net.minecraft.tags.FluidTags.WATER)) {
					return true;
				}
				if (!isPassable(level, below, level.getBlockState(below))) {
					return true;
				}
			}
			return false;
		}
		return true;
	}
}
