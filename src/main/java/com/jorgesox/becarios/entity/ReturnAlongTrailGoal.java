package com.jorgesox.becarios.entity;

import java.util.Deque;
import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Al terminar un trabajo lejos (túneles, exploración), vuelve por el mismo
 * camino que hizo, paso a paso, hasta que puede ir directo hacia el dueño.
 * Es lo que haría un jugador: deshacer el camino en vez de quedarse atascado.
 */
public class ReturnAlongTrailGoal extends Goal {
	private static final double OWNER_CLOSE_SQ = 10.0 * 10.0;

	private final BecarioEntity becario;
	private int recalc;
	private int stuck;

	public ReturnAlongTrailGoal(BecarioEntity becario) {
		this.becario = becario;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity owner = becario.getOwner();
		if (becario.getTask() != null || becario.getTrail().isEmpty() || owner == null
			|| becario.isOrderedToSit() || becario.getCombatMode() == CombatMode.GUARDIA) {
			return false;
		}
		if (owner.level() != becario.level() || becario.distanceToSqr(owner) < OWNER_CLOSE_SQ) {
			becario.clearTrail(); // ya está con el dueño
			return false;
		}
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		recalc = 0;
		stuck = 0;
	}

	@Override
	public void stop() {
		becario.getNavigation().stop();
		becario.setSprinting(false);
	}

	@Override
	public void tick() {
		Deque<BlockPos> trail = becario.getTrail();
		BlockPos next = trail.peekLast();
		if (next == null) {
			return;
		}
		if (becario.blockPosition().distSqr(next) <= 2.0) {
			trail.pollLast();
			stuck = 0;
			recalc = 0;
			return;
		}
		becario.setSprinting(true);
		if (--recalc <= 0) {
			recalc = adjustedTickDelay(10);
			becario.getNavigation().moveTo(next.getX() + 0.5, next.getY(), next.getZ() + 0.5, 1.0);
		}
		// Atascado: tapar lava del camino; si sigue sin poder, saltar ese punto del rastro.
		if (++stuck % 20 == 0 && becario.level() instanceof net.minecraft.server.level.ServerLevel level) {
			becario.bridgeToward(level, net.minecraft.world.phys.Vec3.atCenterOf(next));
		}
		if (stuck > 100) {
			trail.pollLast();
			stuck = 0;
		}
	}
}
