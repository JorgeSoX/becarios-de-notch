package com.jorgesox.becarios.entity;

import java.util.EnumSet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.pathfinder.PathType;

/**
 * Seguir al dueño ANDANDO o CORRIENDO, como un jugador. Nunca se teletransporta
 * (a diferencia de FollowOwnerGoal vanilla, que tepea al lobo a >12 bloques).
 *
 * - Empieza a seguir a más de START bloques (o siempre, si se le ordenó "ven").
 * - Corre (sprint) si estás lejos, anda si estás cerca.
 * - Si lleva un rato sin acercarse, te avisa una vez de que no encuentra camino.
 */
public class BecarioFollowOwnerGoal extends Goal {
	private static final double START = 6.0;
	private static final double STOP = 2.5;
	private static final double SPRINT_FROM = 10.0;
	private static final int STUCK_TICKS = 600;          // 30 s sin progreso
	private static final int STUCK_WARN_COOLDOWN = 1200; // avisar como mucho cada 60 s

	private final BecarioEntity becario;
	private LivingEntity owner;
	private int recalc;
	private float oldWaterCost;
	private double bestDistSq;
	private int noProgressTicks;
	private long lastWarnTick = -STUCK_WARN_COOLDOWN;

	public BecarioFollowOwnerGoal(BecarioEntity becario) {
		this.becario = becario;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		// Si está en un equipo, sigue a su jefe; "ven aquí" siempre es hacia ti.
		LivingEntity o = becario.isComing() ? becario.getOwner() : becario.getLeader();
		if (o == null || o.level() != becario.level() || becario.unableToMoveToOwner()
			|| !becario.getTrail().isEmpty()
			|| becario.getCombatMode() == CombatMode.GUARDIA) {
			return false;
		}
		double start = becario.isComing() ? STOP : START;
		if (becario.distanceToSqr(o) < start * start) {
			becario.setComing(false);
			return false;
		}
		this.owner = o;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return owner != null
			&& owner.isAlive()
			&& owner.level() == becario.level()
			&& !becario.unableToMoveToOwner()
			&& becario.getCombatMode() != CombatMode.GUARDIA
			&& becario.distanceToSqr(owner) > STOP * STOP;
	}

	@Override
	public void start() {
		recalc = 0;
		noProgressTicks = 0;
		bestDistSq = Double.MAX_VALUE;
		oldWaterCost = becario.getPathfindingMalus(PathType.WATER);
		becario.setPathfindingMalus(PathType.WATER, 0.0F);
	}

	@Override
	public void stop() {
		owner = null;
		becario.setComing(false);
		becario.setSprinting(false);
		becario.getNavigation().stop();
		becario.setPathfindingMalus(PathType.WATER, oldWaterCost);
	}

	@Override
	public void tick() {
		becario.getLookControl().setLookAt(owner, 10.0F, becario.getMaxHeadXRot());
		double distSq = becario.distanceToSqr(owner);
		becario.setSprinting(distSq > SPRINT_FROM * SPRINT_FROM);

		if (--recalc <= 0) {
			recalc = adjustedTickDelay(10);
			becario.getNavigation().moveTo(owner, 1.0);
		}

		// Detección de atasco: ¿se ha acercado al menos 1 bloque en los últimos 10 s?
		if (distSq < bestDistSq - 1.0) {
			bestDistSq = distSq;
			noProgressTicks = 0;
		} else if (++noProgressTicks % 20 == 0 && noProgressTicks >= 40 && becario.level() instanceof ServerLevel level) {
			// Atascado: ¿hay lava o un hueco en el camino? Taparlo como haría un jugador.
			becario.bridgeToward(level, owner.position());
		}
		if (noProgressTicks > STUCK_TICKS) {
			noProgressTicks = 0;
			bestDistSq = distSq;
			long now = becario.level().getGameTime();
			if (now - lastWarnTick > STUCK_WARN_COOLDOWN) {
				lastWarnTick = now;
				becario.tell("no encuentro camino hasta ti. Ven a buscarme o ábreme paso.");
			}
		}
	}
}
